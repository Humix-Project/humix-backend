package com.humix.api.domain.musicGeneration.service;

import com.humix.api.domain.musicGeneration.client.RunpodClient;
import com.humix.api.domain.musicGeneration.entity.MusicGeneration;
import com.humix.api.domain.musicGeneration.repository.MusicGenerationRepository;
import com.humix.api.domain.musicGeneration.type.GenerationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * SSE로 구독 중인 작업의 RunPod 상태를 주기적으로 확인한다. (#64)
 *
 * 대기열에 있는 동안은 AI 코드가 실행 전이라 진행 보고가 오지 않는다. 그래서 RunPod 상태로 "아직 살아 있음"을 확인해
 * 기다림을 연장하고, RunPod가 실패로 끝낸 작업은 바로 실패 처리한다. 완료됐는데 콜백이 오지 않는 작업은
 * 소식이 갱신되지 않으므로 유휴 한도를 넘기면 실패 처리된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunpodQueueWatcher {

    private static final Set<String> FAILED_STATUSES = Set.of("FAILED", "CANCELLED", "TIMED_OUT");

    private final MusicGenerationService musicGenerationService;
    private final MusicGenerationRepository musicGenerationRepository;
    private final RunpodClient runpodClient;

    @Scheduled(fixedDelayString = "${runpod.watch.poll-interval-ms:15000}")
    public void watch() {
        musicGenerationService.pruneTracking();

        for (String taskId : musicGenerationService.activeTaskIds()) {
            try {
                check(taskId);
            } catch (Exception e) {
                log.warn("[RunpodWatch] 작업 확인 중 오류. taskId: {}, reason: {}", taskId, e.getMessage());
            }
        }
    }

    private void check(String taskId) {
        MusicGeneration generation = musicGenerationRepository.findByTaskId(taskId).orElse(null);
        if (generation == null || generation.getStatus() != GenerationStatus.PROCESSING) {
            return; // 끝난 작업의 emitter는 완료·취소 처리에서 정리된다
        }

        if (generation.getRunpodJobId() != null) {
            runpodClient.getStatus(generation.getRunpodJobId()).ifPresent(job -> handle(taskId, job));
        }

        if (musicGenerationService.isIdleExpired(taskId)) {
            musicGenerationService.failTask(taskId, "마지막 소식 이후 유휴 한도를 넘었습니다.");
        }
    }

    private void handle(String taskId, RunpodClient.JobStatus job) {
        String status = job.status();
        if (status == null) {
            return;
        }
        if (FAILED_STATUSES.contains(status)) {
            musicGenerationService.failTask(taskId, "RunPod 작업 상태: " + status);
        } else if ("IN_QUEUE".equals(status)) {
            musicGenerationService.touch(taskId);
            musicGenerationService.notifyQueued(taskId);
        } else if ("IN_PROGRESS".equals(status)) {
            musicGenerationService.touch(taskId);
        }
        // COMPLETED: 완료 콜백이 곧 도착한다. 갱신하지 않으므로 콜백이 끝내 없으면 유휴 한도 후 실패 처리된다.
    }
}
