package com.humix.api.domain.musicGeneration.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * RunPod Serverless 엔드포인트 호출을 한곳에 모은 클라이언트.
 * - submit: POST /run 으로 작업을 넣고 RunPod 작업 ID를 돌려준다.
 * - getStatus: GET /status/{jobId} 로 대기(delayTime)·실행(executionTime) 시간을 조회한다.
 */
@Slf4j
@Component
public class RunpodClient {

    private static final Duration SUBMIT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration STATUS_TIMEOUT = Duration.ofSeconds(5);
    private static final Set<String> FINISHED_STATUSES = Set.of("COMPLETED", "FAILED", "CANCELLED", "TIMED_OUT");

    private final WebClient webClient;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public RunpodClient(WebClient.Builder webClientBuilder,
                        ObjectMapper objectMapper,
                        @Value("${runpod.endpoint-url}") String endpointUrl,
                        @Value("${runpod.api-key}") String apiKey) {
        // Trailing slash가 없으면 상대 경로(run, status/..)를 붙일 때 마지막 경로 세그먼트가 유실된다.
        String baseUrl = endpointUrl.endsWith("/") ? endpointUrl : endpointUrl + "/";
        this.webClient = webClientBuilder.clone().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    private record RunpodRequest(Object input) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RunResponse(String id, String status) {}

    /** RunPod /status 응답 중 측정에 필요한 값. 시간 단위는 ms. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JobStatus(String id, String status, Long delayTime, Long executionTime) {
        public boolean isFinished() {
            return status != null && FINISHED_STATUSES.contains(status);
        }
    }

    /**
     * 작업을 제출하고 RunPod 작업 ID를 반환한다.
     * HTTP 오류는 그대로 던지지만(작업이 들어가지 않은 것), 응답 본문 파싱 실패는 작업이 이미 들어간 상태이므로
     * 예외 대신 null을 반환해 생성 흐름을 막지 않는다. 이 경우 해당 건은 측정에서만 빠진다.
     */
    public String submit(Object input) {
        String body = webClient.post()
                .uri("run")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .bodyValue(new RunpodRequest(input))
                .retrieve()
                .bodyToMono(String.class)
                .block(SUBMIT_TIMEOUT);

        if (body == null || body.isBlank()) {
            log.warn("[Runpod] /run 응답 본문이 비어 있어 작업 ID를 기록하지 못했습니다.");
            return null;
        }
        try {
            RunResponse response = objectMapper.readValue(body, RunResponse.class);
            log.info("[Runpod] 작업 제출 완료. jobId: {}, status: {}", response.id(), response.status());
            return response.id();
        } catch (Exception e) {
            log.warn("[Runpod] /run 응답을 해석하지 못해 작업 ID를 기록하지 못했습니다. body: {}", body);
            return null;
        }
    }

    /** 작업 상태를 조회한다. 측정용이라 실패해도 예외를 던지지 않고 비어 있는 값을 돌려준다. */
    public Optional<JobStatus> getStatus(String jobId) {
        try {
            String body = webClient.get()
                    .uri("status/{jobId}", jobId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(STATUS_TIMEOUT);
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(body, JobStatus.class));
        } catch (Exception e) {
            log.warn("[Runpod] 작업 상태 조회 실패. jobId: {}, reason: {}", jobId, e.getMessage());
            return Optional.empty();
        }
    }
}
