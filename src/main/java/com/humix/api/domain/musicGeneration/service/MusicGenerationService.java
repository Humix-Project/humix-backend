package com.humix.api.domain.musicGeneration.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import lombok.extern.slf4j.Slf4j;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MusicGenerationService {

    // AI 서버가 업로드하는 결과 오디오용 presigned PUT URL 유효시간. RunPod 콜드스타트·대기열 + 생성 시간을 감안해 넉넉히 둔다.
    private static final Duration PRESIGNED_UPLOAD_EXPIRATION = Duration.ofMinutes(30);

    // 요청/응답 길이 비교 및 허밍 길이 검증에서 허용하는 오차(초). 멜로디가 0.1초 단위로 양자화되기 때문이다.
    private static final double DURATION_TOLERANCE_SECONDS = 0.1;
    private static final double FLOAT_EPSILON = 1e-9;

    private final MusicGenerationRepository musicGenerationRepository;
    private final MelodyScoreRepository melodyScoreRepository;
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

    @Value("${generation.duration-seconds}")
    private int generationDurationSeconds;

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
            @JsonInclude(JsonInclude.Include.NON_NULL) String prompt,
            @JsonProperty("duration_seconds") int durationSeconds,
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
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.MELODY_SCORE_NOT_FOUND));

        // 2. Melody Vectors 파싱 후 생성 길이를 넘는 멜로디는 AI 서버로 보내기 전에 거부
        List<AiMelodyVector> melodyVectors = parseMelodyVectors(melodyScore.getNotesData());
        validateMelodyLength(melodyVectors);

        // 3. Task ID 생성
        String taskId = "task_" + UUID.randomUUID().toString();

        // 4. S3 Presigned URL 생성
        String uniqueFileName = UUID.randomUUID().toString() + "_variation.wav";
        String fileKey = "audio/" + uniqueFileName;
        String presignedUrl = generatePresignedUrl(fileKey);

        // 5. MusicGeneration 엔티티 생성 및 영속화 (길이는 요청한 값으로 먼저 기록하고, 완료 콜백에서 실제 길이로 갱신)
        MusicGeneration musicGeneration = request.from(userDetails.getMember(), melodyScore);
        musicGeneration.updateTaskId(taskId);
        musicGeneration.updateDuration((double) generationDurationSeconds);
        musicGenerationRepository.save(musicGeneration);
        musicGeneration.markAsRoot(); // 최초 생성곡은 자기 자신이 버전 묶음의 root

        // 6. AI 서버로 비동기 작곡 요청
        String callbackUrl = backendUrl + "/api/v1/internal/tasks/" + taskId + "/completion";
        
        log.info("[MusicGeneration] Requesting song generation for taskId: {}, genre: {}, mood: {}, duration: {}s, callback: {}", taskId, request.genre(), request.mood(), generationDurationSeconds, callbackUrl);

        AiGenerationRequest aiRequest = new AiGenerationRequest(
                "generate",
                taskId,
                melodyVectors,
                request.genre(),
                request.mood(),
                musicGeneration.getPrompt(),
                generationDurationSeconds,
                callbackUrl,
                presignedUrl
        );

        // RunPod Serverless: POST /run with {"input": {...}} (Trailing slash treatment & relative URI)
        String baseUrl = runpodEndpointUrl.endsWith("/") ? runpodEndpointUrl : runpodEndpointUrl + "/";
        WebClient webClient = webClientBuilder.baseUrl(baseUrl).build();
        try {
            webClient.post()
                    .uri("run")
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
        MusicGeneration parentGeneration = musicGenerationRepository.findByIdAndDeletedAtIsNull(songId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.GENERATION_NOT_FOUND));

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
        musicGeneration.updateDuration((double) generationDurationSeconds); // 완료 콜백에서 AI가 보고한 실제 길이로 갱신
        musicGenerationRepository.save(musicGeneration);

        // 5. Melody Vectors 파싱
        List<AiMelodyVector> melodyVectors = parseMelodyVectors(melodyScore.getNotesData());

        // 6. AI 서버로 비동기 수정 요청
        String callbackUrl = backendUrl + "/api/v1/internal/tasks/" + taskId + "/completion";

        log.info("[MusicGeneration] Requesting prompt modification for taskId: {}, prompt: {}, callback: {}", taskId, request.prompt(), callbackUrl);

        AiModificationRequest aiRequest = new AiModificationRequest(
                "modify",
                taskId,
                melodyVectors,
                request.prompt(),
                callbackUrl,
                presignedUrl
        );

        // RunPod Serverless: POST /run with {"input": {...}} (Trailing slash treatment & relative URI)
        String baseUrl = runpodEndpointUrl.endsWith("/") ? runpodEndpointUrl : runpodEndpointUrl + "/";
        WebClient webClient = webClientBuilder.baseUrl(baseUrl).build();
        try {
            webClient.post()
                    .uri("run")
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

    /**
     * 특정 버전으로 되돌린다: 같은 root에서 해당 버전보다 뒤(id가 더 큰)의 버전을 모두 soft delete.
     * 같은 root에 진행 중인 작업이 있으면 되돌리기 중에 결과가 들어와 버전이 꼬이므로 거부한다.
     */
    @Transactional
    public MusicGenerationDTO.RevertResponse revertToVersion(CustomUserDetails userDetails, Long generationId) {
        requireLogin(userDetails);

        MusicGeneration target = musicGenerationRepository
                .findByIdAndStatusAndDeletedAtIsNull(generationId, GenerationStatus.COMPLETED)
                .filter(generation -> isOwner(userDetails, generation))
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.GENERATION_NOT_FOUND));
        Long rootId = target.getRootGenerationId();

        if (musicGenerationRepository.existsByRootGenerationIdAndStatusAndDeletedAtIsNull(rootId, GenerationStatus.PROCESSING)) {
            throw new GeneralException(GeneralErrorCode.GENERATION_IN_PROGRESS);
        }

        int deletedCount = musicGenerationRepository.softDeleteVersionsAfter(rootId, target.getId(), LocalDateTime.now());
        log.info("[MusicGeneration] Reverted to generationId: {} (rootId: {}), soft deleted {} versions", generationId, rootId, deletedCount);

        return new MusicGenerationDTO.RevertResponse(target.getId(), deletedCount);
    }

    /**
     * 원본(root) 곡의 유효한 버전(완료 + soft delete 아님) 목록을 생성 순서대로 반환한다.
     * 경로의 ID는 원본(최초 생성곡)의 ID여야 하며, 수정본의 ID이거나 타인의 곡이면 404.
     */
    public MusicGenerationDTO.VersionListResponse getVersions(CustomUserDetails userDetails, Long rootGenerationId) {
        requireLogin(userDetails);

        musicGenerationRepository.findByIdAndDeletedAtIsNull(rootGenerationId)
                .filter(generation -> rootGenerationId.equals(generation.getRootGenerationId()))
                .filter(generation -> isOwner(userDetails, generation))
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.GENERATION_NOT_FOUND));

        List<MusicGeneration> versions = musicGenerationRepository
                .findByRootGenerationIdAndStatusAndDeletedAtIsNullOrderByIdAsc(rootGenerationId, GenerationStatus.COMPLETED);

        // versionNo는 컬럼 없이 조회 순서로 계산한다 (되돌리기로 번호가 비는 일이 없도록)
        List<MusicGenerationDTO.VersionItemResponse> items = new ArrayList<>();
        for (int i = 0; i < versions.size(); i++) {
            items.add(MusicGenerationDTO.VersionItemResponse.of(versions.get(i), i + 1));
        }
        return new MusicGenerationDTO.VersionListResponse(rootGenerationId, items);
    }

    private void requireLogin(CustomUserDetails userDetails) {
        if (userDetails == null || userDetails.getMember() == null) {
            throw new GeneralException(GeneralErrorCode.LOGIN_REQUIRED);
        }
    }

    // 타인의 곡은 존재 여부를 드러내지 않도록 호출부에서 404로 처리한다
    private boolean isOwner(CustomUserDetails userDetails, MusicGeneration generation) {
        return generation.getMember().getUuid().equals(userDetails.getMember().getUuid());
    }

    public SseEmitter subscribeTaskStream(String taskId) {
        log.info("[MusicGeneration] SSE subscribe request received for taskId: {}", taskId);
        
        // 1. DB에서 현재 태스크 상태 조회
        MusicGeneration musicGeneration = musicGenerationRepository.findByTaskId(taskId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.TASK_NOT_FOUND));
                
        SseEmitter emitter = new SseEmitter(1000 * 60 * 5L); // 5분 타임아웃
        
        // 2. 만약 이미 완료된 상태라면 즉시 완료 이벤트 전송 후 종료
        if (musicGeneration.getStatus() == GenerationStatus.COMPLETED) {
            log.info("[MusicGeneration] Task {} is already COMPLETED. Sending immediate complete event.", taskId);
            try {
                emitter.send(SseEmitter.event().name("connect").data("Connected successfully"));
                
                MusicGenerationDTO.CompletionResult result = new MusicGenerationDTO.CompletionResult(
                        taskId,
                        musicGeneration.getId(),
                        musicGeneration.getResultS3Url(),
                        musicGeneration.getDurationSeconds()
                );
                MusicGenerationDTO.CompletionStreamResponse completeResponse = new MusicGenerationDTO.CompletionStreamResponse(
                        "COMPLETED",
                        result
                );
                emitter.send(SseEmitter.event().name("complete").data(completeResponse));
                emitter.complete();
            } catch (Exception e) {
                log.error("[MusicGeneration] Failed to send immediate completion event for taskId: {}: {}", taskId, e.getMessage());
                emitter.completeWithError(e);
            }
            return emitter;
        }
        
        // 3. 만약 실패/취소된 상태라면 즉시 실패/취소 이벤트 전송 후 종료
        if (musicGeneration.getStatus() == GenerationStatus.FAILED || musicGeneration.getStatus() == GenerationStatus.CANCELED) {
            log.info("[MusicGeneration] Task {} is already {}. Sending immediate status event.", taskId, musicGeneration.getStatus());
            try {
                emitter.send(SseEmitter.event().name("connect").data("Connected successfully"));
                emitter.send(SseEmitter.event().name("progress").data(
                        new MusicGenerationDTO.ProgressStreamResponse(taskId, musicGeneration.getStatus().name(), 0)
                ));
                emitter.complete();
            } catch (Exception e) {
                log.error("[MusicGeneration] Failed to send immediate status event for taskId: {}: {}", taskId, e.getMessage());
                emitter.completeWithError(e);
            }
            return emitter;
        }

        // 4. 아직 진행 중인 경우(PROCESSING)에만 emitters 맵에 등록하고 콜백 대기
        emitters.put(taskId, emitter);
        log.info("[MusicGeneration] Registered SseEmitter for active taskId: {}. Total active emitters: {}", taskId, emitters.size());

        emitter.onCompletion(() -> {
            log.info("[MusicGeneration] SseEmitter completed for taskId: {}", taskId);
            emitters.remove(taskId);
        });
        emitter.onTimeout(() -> {
            log.info("[MusicGeneration] SseEmitter timeout for taskId: {}", taskId);
            emitter.complete();
            emitters.remove(taskId);
        });
        emitter.onError((e) -> {
            log.warn("[MusicGeneration] SseEmitter error for taskId: {}: {}", taskId, e.getMessage());
            emitter.complete();
            emitters.remove(taskId);
        });

        // 초기 연결 성공 및 0% 진행 상태 전송
        try {
            emitter.send(SseEmitter.event().name("connect").data("Connected successfully"));
            
            MusicGenerationDTO.ProgressStreamResponse initialProgress = new MusicGenerationDTO.ProgressStreamResponse(
                    taskId, "PROCESSING", 0
            );
            emitter.send(SseEmitter.event().name("progress").data(initialProgress));
        } catch (Exception e) {
            log.error("[MusicGeneration] Failed to send initial progress to SseEmitter for taskId: {}: {}", taskId, e.getMessage());
            emitter.complete();
            emitters.remove(taskId);
        }

        return emitter;
    }

    @Transactional
    public MusicGenerationDTO.TaskCancelResponse cancelTask(String taskId) {
        MusicGeneration musicGeneration = musicGenerationRepository.findByTaskId(taskId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.TASK_NOT_FOUND));

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
        log.info("[MusicGeneration] Received completion callback for taskId: {}, audioUrl: {}", taskId, request.generatedAudioUrl());
        MusicGeneration musicGeneration = musicGenerationRepository.findByTaskId(taskId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.TASK_NOT_FOUND));

        String audioUrl = request.generatedAudioUrl();
        boolean isFailed = "FAILED".equalsIgnoreCase(audioUrl);

        if (!isFailed && isDurationMismatch(musicGeneration, request.durationSeconds())) {
            log.warn("[MusicGeneration] Duration mismatch for taskId: {}. requested: {}s, reported: {}s. Marking as FAILED.",
                    taskId, musicGeneration.getDurationSeconds(), request.durationSeconds());
            isFailed = true;
        }

        if (isFailed) {
            musicGeneration.updateStatus(GenerationStatus.FAILED, null);
        } else {
            musicGeneration.updateStatus(GenerationStatus.COMPLETED, audioUrl);
            if (request.durationSeconds() != null) {
                musicGeneration.updateDuration(request.durationSeconds());
            }
        }

        SseEmitter emitter = emitters.remove(taskId);
        if (emitter != null) {
            log.info("[MusicGeneration] Found active SseEmitter for taskId: {}. Sending event...", taskId);
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
                log.info("[MusicGeneration] Successfully sent completion event to SseEmitter for taskId: {}", taskId);
            } catch (Exception e) {
                log.error("[MusicGeneration] Failed to send completion event to SseEmitter for taskId: {}: {}", taskId, e.getMessage());
            }
        } else {
            log.warn("[MusicGeneration] No active SseEmitter found for taskId: {} (probably timed out or client disconnected). Current emitter keys: {}", taskId, emitters.keySet());
        }
    }

    private String generatePresignedUrl(String fileKey) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(fileKey)
                .contentType("audio/wav")
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(PRESIGNED_UPLOAD_EXPIRATION)
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);
        return presignedRequest.url().toString();
    }

    // 멜로디의 마지막 음이 끝나는 시점이 생성 길이를 (허용 오차 이상) 넘으면 거부한다.
    private void validateMelodyLength(List<AiMelodyVector> melodyVectors) {
        double melodyEnd = melodyVectors.stream()
                .mapToDouble(vector -> vector.onsetSeconds() + vector.durationSeconds())
                .max()
                .orElse(0.0);

        if (exceedsTolerance(melodyEnd - generationDurationSeconds)) {
            throw new GeneralException(GeneralErrorCode.HUMMING_TOO_LONG);
        }
    }

    // 최초 생성(generate) 작업만 요청한 길이와 AI가 보고한 길이를 비교한다.
    // 보고된 길이가 없으면(구 AI 서버) 비교하지 않고 요청한 길이를 그대로 둔다.
    private boolean isDurationMismatch(MusicGeneration musicGeneration, Double reportedDuration) {
        if (reportedDuration == null
                || musicGeneration.getParentGeneration() != null
                || musicGeneration.getDurationSeconds() == null) {
            return false;
        }
        return exceedsTolerance(Math.abs(reportedDuration - musicGeneration.getDurationSeconds()));
    }

    // 부동소수점 오차 때문에 경계값(예: 정확히 0.1초 차이)이 잘못 거부되지 않도록 아주 작은 보정값을 둔다.
    private static boolean exceedsTolerance(double deviationSeconds) {
        return deviationSeconds > DURATION_TOLERANCE_SECONDS + FLOAT_EPSILON;
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
