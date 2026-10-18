package com.humix.api.domain.musicGeneration.service;

import com.humix.api.domain.musicGeneration.client.RunpodClient;
import com.humix.api.domain.musicGeneration.entity.MusicGeneration;
import com.humix.api.domain.musicGeneration.repository.MusicGenerationRepository;
import com.humix.api.domain.musicGeneration.type.GenerationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 끝난 생성 작업의 RunPod 대기·실행 시간을 주기적으로 채운다.
 *
 * 완료 콜백 시점에는 AI 서버의 핸들러가 아직 반환 전이라 RunPod 상태가 IN_PROGRESS일 수 있다.
 * 그래서 콜백에서 바로 조회하지 않고, 작업이 RunPod에서 완전히 끝난 뒤 따로 가져온다.
 * RunPod는 /run 결과를 일정 시간만 보관하므로, 최근 작업만 조회한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunpodMetricsCollector {

    // RunPod가 /status 결과를 보관하는 시간보다 짧게 잡는다. 이 시간이 지나면 측정을 포기한다.
    private static final Duration LOOKBACK = Duration.ofMinutes(30);
    // RunPod가 시간 값을 주지 않은 채 끝난 작업은 -1로 표시해 다시 조회하지 않는다.
    private static final long UNKNOWN = -1L;
    private static final List<GenerationStatus> FINISHED =
            List.of(GenerationStatus.COMPLETED, GenerationStatus.FAILED, GenerationStatus.CANCELED);

    private final MusicGenerationRepository musicGenerationRepository;
    private final RunpodClient runpodClient;

    @Scheduled(fixedDelayString = "${runpod.metrics.poll-interval-ms:30000}")
    public void collect() {
        List<MusicGeneration> targets = musicGenerationRepository
                .findTop20ByRunpodJobIdIsNotNullAndQueueDelayMsIsNullAndStatusInAndCreatedAtAfter(
                        FINISHED, LocalDateTime.now().minus(LOOKBACK));

        for (MusicGeneration generation : targets) {
            runpodClient.getStatus(generation.getRunpodJobId())
                    .filter(RunpodClient.JobStatus::isFinished)
                    .ifPresent(status -> record(generation, status));
        }
    }

    private void record(MusicGeneration generation, RunpodClient.JobStatus status) {
        long delayMs = status.delayTime() != null ? status.delayTime() : UNKNOWN;
        long executionMs = status.executionTime() != null ? status.executionTime() : UNKNOWN;
        musicGenerationRepository.updateRunpodTimings(generation.getId(), delayMs, executionMs);

        Long endToEndMs = (generation.getCreatedAt() != null && generation.getCompletedAt() != null)
                ? Duration.between(generation.getCreatedAt(), generation.getCompletedAt()).toMillis()
                : null;

        log.info("[RunpodMetrics] taskId: {}, jobId: {}, runpodStatus: {}, queueDelayMs: {}, executionMs: {}, endToEndMs: {}",
                generation.getTaskId(), generation.getRunpodJobId(), status.status(), delayMs, executionMs, endToEndMs);
    }
}
