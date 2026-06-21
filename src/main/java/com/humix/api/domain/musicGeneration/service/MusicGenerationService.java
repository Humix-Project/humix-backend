package com.humix.api.domain.musicGeneration.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.humix.api.domain.humming.entity.ReferenceTrack;
import com.humix.api.domain.humming.repository.ReferenceTrackRepository;
import com.humix.api.domain.melodyScore.dto.MelodyScoreDTO;
import com.humix.api.domain.melodyScore.entity.MelodyScore;
import com.humix.api.domain.melodyScore.repository.MelodyScoreRepository;
import com.humix.api.domain.musicGeneration.dto.MusicGenerationDTO;
import com.humix.api.domain.musicGeneration.entity.MusicGeneration;
import com.humix.api.domain.musicGeneration.repository.MusicGenerationRepository;
import com.humix.api.domain.musicGeneration.type.GenerationStatus;
import com.humix.api.global.apiPayload.code.GeneralErrorCode;
import com.humix.api.global.apiPayload.exception.GeneralException;
import com.humix.api.global.security.userdetails.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MusicGenerationService {

    private final MusicGenerationRepository musicGenerationRepository;
    private final MelodyScoreRepository melodyScoreRepository;
    private final ReferenceTrackRepository referenceTrackRepository;
    private final S3Presigner s3Presigner;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${ai.server.url}")
    private String aiServerUrl;

    @Value("${backend.url}")
    private String backendUrl;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${runpod.endpoint-url}")
    private String runpodEndpointUrl;

    @Value("${runpod.api-key}")
    private String runpodApiKey;

    // Concurrent map to keep track of active SSE Emitters
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    private record AiMelodyVector(
            int pitch,
            @JsonProperty("onset_seconds") double onsetSeconds,
            @JsonProperty("duration_seconds") double durationSeconds
    ) {}

    private record AiGenerationRequest(
            String action,
            @JsonProperty("task_id") String taskId,
            @JsonProperty("melody_vectors") List<AiMelodyVector> melodyVectors,
            String genre,
            String mood,
            @JsonProperty("reference_track") String referenceTrack,
            @JsonProperty("callback_url") String callbackUrl,
            @JsonProperty("presigned_url") String presignedUrl
    ) {}

    private record AiModificationRequest(
            String action,
            @JsonProperty("task_id") String taskId,
            @JsonProperty("melody_vectors") List<AiMelodyVector> melodyVectors,
            String prompt,
            @JsonProperty("callback_url") String callbackUrl,
            @JsonProperty("presigned_url") String presignedUrl
    ) {}

    // RunPod Serverless 요청 래퍼: {"input": {...}}
    private record RunpodRequest(Object input) {}

    @Transactional
    public MusicGenerationDTO.TaskAcceptedResponse generateSong(CustomUserDetails userDetails,
                                                                MusicGenerationDTO.SongCreateRequest request) {
        if (userDetails == null || userDetails.getMember() == null) {
            throw new GeneralException(GeneralErrorCode.LOGIN_REQUIRED);
        }

        // 1. MelodyScore 로드
        MelodyScore melodyScore = melodyScoreRepository.findByHummingId(request.hummingId())
                .orElseThrow(() -> new IllegalArgumentException("해당 허밍의 멜로디 악보가 존재하지 않습니다. ID: " + request.hummingId()));

        // 2. ReferenceTrack 로드 (옵션)
        ReferenceTrack referenceTrack = null;
        if (request.referenceTrackId() != null) {
            referenceTrack = referenceTrackRepository.findById(request.referenceTrackId())
                    .orElseThrow(() -> new IllegalArgumentException("해당 참조 곡이 존재하지 않습니다. ID: " + request.referenceTrackId()));
        }

        // 3. Task ID 생성
        String taskId = "task_" + UUID.randomUUID().toString();

        // 4. S3 Presigned URL 생성
        String uniqueFileName = UUID.randomUUID().toString() + "_variation.wav";
        String fileKey = "audio/" + uniqueFileName;
        String presignedUrl = generatePresignedUrl(fileKey);

        // 5. MusicGeneration 엔티티 생성 및 영속화
        MusicGeneration musicGeneration = request.from(userDetails.getMember(), melodyScore);
        musicGeneration.updateTaskId(taskId);
        musicGeneration.updateDuration(30); // Default 30s
        musicGenerationRepository.save(musicGeneration);

        // 6. Melody Vectors 파싱
        List<AiMelodyVector> melodyVectors = parseMelodyVectors(melodyScore.getNotesData());

        // 7. AI 서버로 비동기 작곡 요청
        String refTrackName = referenceTrack != null ? referenceTrack.getAudioName() : null;
        String callbackUrl = backendUrl + "/api/v1/internal/tasks/" + taskId + "/completion";
        
        AiGenerationRequest aiRequest = new AiGenerationRequest(
                "generate",
                taskId,
                melodyVectors,
                request.genre(),
                request.mood(),
                refTrackName,
                callbackUrl,
                presignedUrl
        );

        // RunPod Serverless: POST /run with {"input": {...}}
        WebClient webClient = webClientBuilder.baseUrl(runpodEndpointUrl).build();
        try {
            webClient.post()
                    .uri("/run")
                    .header("Authorization", "Bearer " + runpodApiKey)
                    .bodyValue(new RunpodRequest(aiRequest))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (Exception e) {
            throw new IllegalStateException("AI 서버로 노래 생성 요청을 전송하는데 실패했습니다.", e);
        }

        return new MusicGenerationDTO.TaskAcceptedResponse(taskId);
    }

    @Transactional
    public MusicGenerationDTO.TaskAcceptedResponse modifySongPrompt(CustomUserDetails userDetails,
                                                                    Long songId,
                                                                    MusicGenerationDTO.SongModificationRequest request) {
        if (userDetails == null || userDetails.getMember() == null) {
            throw new GeneralException(GeneralErrorCode.LOGIN_REQUIRED);
        }

        // 1. 기존 완성곡(Parent) 로드
        MusicGeneration parentGeneration = musicGenerationRepository.findById(songId)
                .orElseThrow(() -> new IllegalArgumentException("수정의 대상이 되는 원본 곡이 존재하지 않습니다. ID: " + songId));

        MelodyScore melodyScore = parentGeneration.getMelodyScore();

        // 2. Task ID 생성
        String taskId = "task_" + UUID.randomUUID().toString();

        // 3. S3 Presigned URL 생성
        String uniqueFileName = UUID.randomUUID().toString() + "_variation.wav";
        String fileKey = "audio/" + uniqueFileName;
        String presignedUrl = generatePresignedUrl(fileKey);

        // 4. 새로운 MusicGeneration 엔티티 생성 및 영속화
        MusicGeneration musicGeneration = request.from(userDetails.getMember(), melodyScore, parentGeneration);
        musicGeneration.updateTaskId(taskId);
        musicGeneration.updateDuration(30); // Default 30s
        musicGenerationRepository.save(musicGeneration);

        // 5. Melody Vectors 파싱
        List<AiMelodyVector> melodyVectors = parseMelodyVectors(melodyScore.getNotesData());

        // 6. AI 서버로 비동기 수정 요청
        String callbackUrl = backendUrl + "/api/v1/internal/tasks/" + taskId + "/completion";

        AiModificationRequest aiRequest = new AiModificationRequest(
                "modify",
                taskId,
                melodyVectors,
                request.prompt(),
                callbackUrl,
                presignedUrl
        );

        // RunPod Serverless: POST /run with {"input": {...}}
        WebClient webClient = webClientBuilder.baseUrl(runpodEndpointUrl).build();
        try {
            webClient.post()
                    .uri("/run")
                    .header("Authorization", "Bearer " + runpodApiKey)
                    .bodyValue(new RunpodRequest(aiRequest))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (Exception e) {
            throw new IllegalStateException("AI 서버로 노래 수정 요청을 전송하는데 실패했습니다.", e);
        }

        return new MusicGenerationDTO.TaskAcceptedResponse(taskId);
    }

    public SseEmitter subscribeTaskStream(String taskId) {
        SseEmitter emitter = new SseEmitter(1000 * 60 * 5L); // 5분 타임아웃
        
        emitters.put(taskId, emitter);

        emitter.onCompletion(() -> emitters.remove(taskId));
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(taskId);
        });
        emitter.onError((e) -> {
            emitter.complete();
            emitters.remove(taskId);
        });

        // 초기 연결 성공 통지 및 0% progress 전송
        try {
            emitter.send(SseEmitter.event().name("connect").data("Connected successfully"));
            
            MusicGenerationDTO.ProgressStreamResponse initialProgress = new MusicGenerationDTO.ProgressStreamResponse(
                    taskId, "PROCESSING", 0
            );
            emitter.send(SseEmitter.event().name("progress").data(initialProgress));
        } catch (Exception e) {
            emitter.complete();
            emitters.remove(taskId);
        }

        return emitter;
    }

    @Transactional
    public MusicGenerationDTO.TaskCancelResponse cancelTask(String taskId) {
        MusicGeneration musicGeneration = musicGenerationRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("해당 태스크 ID의 작업 정보가 존재하지 않습니다. ID: " + taskId));

        musicGeneration.updateStatus(GenerationStatus.CANCELED, null);

        // SSE 알림 및 emitter 종료
        SseEmitter emitter = emitters.remove(taskId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("progress").data(
                        new MusicGenerationDTO.ProgressStreamResponse(taskId, "CANCELED", 0)
                ));
                emitter.complete();
            } catch (Exception e) {
                // ignore
            }
        }

        return new MusicGenerationDTO.TaskCancelResponse(taskId, "CANCELED");
    }

    @Transactional
    public void completeTask(String taskId, MusicGenerationDTO.AiTaskCompletionRequest request) {
        MusicGeneration musicGeneration = musicGenerationRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("해당 태스크 ID의 작업 정보가 존재하지 않습니다. ID: " + taskId));

        String audioUrl = request.generatedAudioUrl();
        boolean isFailed = "FAILED".equalsIgnoreCase(audioUrl);

        if (isFailed) {
            musicGeneration.updateStatus(GenerationStatus.FAILED, null);
        } else {
            musicGeneration.updateStatus(GenerationStatus.COMPLETED, audioUrl);
        }

        SseEmitter emitter = emitters.remove(taskId);
        if (emitter != null) {
            try {
                if (isFailed) {
                    emitter.send(SseEmitter.event().name("progress").data(
                            new MusicGenerationDTO.ProgressStreamResponse(taskId, "FAILED", 0)
                    ));
                } else {
                    // 완료 응답 전송
                    MusicGenerationDTO.CompletionResult result = new MusicGenerationDTO.CompletionResult(
                            taskId,
                            musicGeneration.getId(),
                            audioUrl,
                            musicGeneration.getDurationSeconds()
                    );
                    MusicGenerationDTO.CompletionStreamResponse completeResponse = new MusicGenerationDTO.CompletionStreamResponse(
                            "COMPLETED",
                            result
                    );
                    emitter.send(SseEmitter.event().name("complete").data(completeResponse));
                }
                emitter.complete();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private String generatePresignedUrl(String fileKey) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(fileKey)
                .contentType("audio/wav")
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        return presignedRequest.url().toString();
    }

    private List<AiMelodyVector> parseMelodyVectors(String notesData) {
        try {
            List<MelodyScoreDTO.NoteDto> notes = objectMapper.readValue(
                    notesData,
                    new TypeReference<List<MelodyScoreDTO.NoteDto>>() {}
            );

            List<AiMelodyVector> vectors = new ArrayList<>();
            for (MelodyScoreDTO.NoteDto note : notes) {
                vectors.add(new AiMelodyVector(
                        note.pitch(),
                        note.startTimeSeconds(),
                        note.durationSeconds()
                ));
            }
            return vectors;
        } catch (IOException e) {
            throw new IllegalStateException("노트 데이터를 파싱하는데 실패했습니다.", e);
        }
    }
}
