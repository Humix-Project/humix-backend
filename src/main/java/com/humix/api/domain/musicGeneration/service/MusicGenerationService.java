package com.humix.api.domain.musicGeneration.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.humix.api.domain.melodyScore.dto.MelodyScoreDTO;
import com.humix.api.domain.melodyScore.entity.MelodyScore;
import com.humix.api.domain.melodyScore.repository.MelodyScoreRepository;
import com.humix.api.domain.musicGeneration.client.RunpodClient;
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

    // 요청/응답 길이 비교 및 허밍 길이 검증에서 허용하는 오차(초). 음길이 반올림이 쌓인 오차와 AI 출력 길이의 미세한 차이를 흡수한다.
    private static final double DURATION_TOLERANCE_SECONDS = 0.1;
    private static final double FLOAT_EPSILON = 1e-9;

    // 구간 재생성에서 허용하는 최소 구간 길이(초). 최대는 원곡 길이(전체 재생성 가능)
    private static final int MIN_SECTION_SECONDS = 5;

    private final MusicGenerationRepository musicGenerationRepository;
    private final MelodyScoreRepository melodyScoreRepository;
    private final S3Presigner s3Presigner;
    private final ObjectMapper objectMapper;
    private final RunpodClient runpodClient;

    @Value("${ai.server.url}")
    private String aiServerUrl;

    @Value("${backend.url}")
    private String backendUrl;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

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

    // 구간 재생성(modify) 요청. 구간 자르기·문맥 선택·크로스페이드·병합은 AI 서버가 맡고, duration_seconds는 보내지 않는다.
    private record AiModificationRequest(
            String action,
            @JsonProperty("task_id") String taskId,
            @JsonProperty("melody_vectors") List<AiMelodyVector> melodyVectors,
            String genre,
            String mood,
            @JsonInclude(JsonInclude.Include.NON_NULL) String prompt,
            @JsonProperty("source_audio_url") String sourceAudioUrl,
            @JsonProperty("section_start_seconds") int sectionStartSeconds,
            @JsonProperty("section_end_seconds") int sectionEndSeconds,
            @JsonProperty("callback_url") String callbackUrl,
            @JsonProperty("presigned_url") String presignedUrl
    ) {}

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

        // RunPod Serverless: POST /run. 응답의 작업 ID는 콜드 스타트 측정(/status 조회)에 쓴다.
        try {
            musicGeneration.recordRunpodJob(runpodClient.submit(aiRequest));
        } catch (Exception e) {
            throw new IllegalStateException("AI 서버로 노래 생성 요청을 전송하는데 실패했습니다.", e);
        }

        return new MusicGenerationDTO.TaskAcceptedResponse(taskId);
    }

    /**
     * 선택한 구간만 다시 생성한 새 버전을 만든다. 원곡(수정 대상 버전)은 그대로 두고 새 generation_id를 부여한다.
     * 오디오 처리는 AI 서버가 전담하며, 백엔드는 구간 정보와 원곡 URL을 넘기고 완료 콜백에서 결과 길이를 검증한다.
     */
    @Transactional
    public MusicGenerationDTO.TaskAcceptedResponse modifySongSection(CustomUserDetails userDetails,
                                                                     Long generationId,
                                                                     MusicGenerationDTO.SongModificationRequest request) {
        requireLogin(userDetails);

        // 1. 수정 대상 버전(Parent) 로드: 완료된 본인 곡이며 삭제되지 않았고 결과 오디오가 있어야 한다
        MusicGeneration parentGeneration = musicGenerationRepository
                .findByIdAndStatusAndDeletedAtIsNull(generationId, GenerationStatus.COMPLETED)
                .filter(generation -> isOwner(userDetails, generation))
                .filter(this::hasResultAudio)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.GENERATION_NOT_FOUND));

        // 2. 구간 검증 및 같은 곡의 진행 중인 작업 차단
        validateSection(request.startTimeSec(), request.endTimeSec(), parentGeneration.getDurationSeconds());
        if (musicGenerationRepository.existsByRootGenerationIdAndStatusAndDeletedAtIsNull(
                parentGeneration.getRootGenerationId(), GenerationStatus.PROCESSING)) {
            throw new GeneralException(GeneralErrorCode.GENERATION_IN_PROGRESS);
        }

        MelodyScore melodyScore = parentGeneration.getMelodyScore();

        // 3. Task ID 생성
        String taskId = "task_" + UUID.randomUUID().toString();

        // 4. S3 Presigned URL 생성 (원곡 키를 덮어쓰지 않도록 요청마다 새 키)
        String uniqueFileName = UUID.randomUUID().toString() + "_variation.wav";
        String fileKey = "audio/" + uniqueFileName;
        String presignedUrl = generatePresignedUrl(fileKey);

        // 5. 새로운 MusicGeneration 엔티티 생성 및 영속화
        MusicGeneration musicGeneration = request.from(userDetails.getMember(), melodyScore, parentGeneration);
        musicGeneration.updateTaskId(taskId);
        // 병합된 전체 곡이므로 결과 길이는 원곡과 같아야 한다. 완료 콜백에서 이 값과 비교한 뒤 AI가 보고한 실제 길이로 갱신
        musicGeneration.updateDuration(parentGeneration.getDurationSeconds());
        musicGenerationRepository.save(musicGeneration);

        // 6. Melody Vectors 파싱 (곡 전체 분량, 원곡 기준 시점 그대로)
        List<AiMelodyVector> melodyVectors = parseMelodyVectors(melodyScore.getNotesData());

        // 7. AI 서버로 비동기 구간 재생성 요청
        String callbackUrl = backendUrl + "/api/v1/internal/tasks/" + taskId + "/completion";

        log.info("[MusicGeneration] Requesting section modification for taskId: {}, sourceGenerationId: {}, section: {}~{}s, prompt: {}, callback: {}",
                taskId, parentGeneration.getId(), request.startTimeSec(), request.endTimeSec(), musicGeneration.getPrompt(), callbackUrl);

        AiModificationRequest aiRequest = new AiModificationRequest(
                "modify",
                taskId,
                melodyVectors,
                musicGeneration.getGenre(),
                musicGeneration.getAtmosphere(),
                musicGeneration.getPrompt(),
                parentGeneration.getResultS3Url(),
                request.startTimeSec(),
                request.endTimeSec(),
                callbackUrl,
                presignedUrl
        );

        // RunPod Serverless: POST /run. 응답의 작업 ID는 콜드 스타트 측정(/status 조회)에 쓴다.
        try {
            musicGeneration.recordRunpodJob(runpodClient.submit(aiRequest));
        } catch (Exception e) {
            throw new IllegalStateException("AI 서버로 구간 재생성 요청을 전송하는데 실패했습니다.", e);
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
                        musicGeneration.getRootGenerationId(),
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

        musicGeneration.recordCompletedAt(LocalDateTime.now());

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
                            musicGeneration.getRootGenerationId(),
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

    // 구간 재생성의 소스 오디오는 결과 URL과 길이가 모두 있어야 한다.
    private boolean hasResultAudio(MusicGeneration generation) {
        return generation.getResultS3Url() != null && !generation.getResultS3Url().isBlank()
                && generation.getDurationSeconds() != null;
    }

    // 0 ≤ start, 구간 길이 ≥ 5초, end ≤ 원곡 길이 (원곡 길이는 0.1초 오차 허용: 29.95초짜리 곡의 끝 30초도 전체 재생성으로 본다)
    private void validateSection(int startTimeSec, int endTimeSec, double sourceDurationSeconds) {
        if (startTimeSec < 0
                || endTimeSec - startTimeSec < MIN_SECTION_SECONDS
                || exceedsTolerance(endTimeSec - sourceDurationSeconds)) {
            throw new GeneralException(GeneralErrorCode.INVALID_SECTION_RANGE);
        }
    }

    // 요청한 길이(최초 생성은 설정값, 구간 재생성은 원곡 길이)와 AI가 보고한 길이를 비교한다.
    // 보고된 길이가 없으면(구 AI 서버) 비교하지 않고 요청 시 기록한 길이를 그대로 둔다.
    private boolean isDurationMismatch(MusicGeneration musicGeneration, Double reportedDuration) {
        if (reportedDuration == null || musicGeneration.getDurationSeconds() == null) {
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
