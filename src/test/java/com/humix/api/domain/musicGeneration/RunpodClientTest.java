package com.humix.api.domain.musicGeneration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.humix.api.domain.musicGeneration.client.RunpodClient;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunpodClientTest {

    private static final String ENDPOINT = "https://api.runpod.ai/v2/test-endpoint";

    private RunpodClient clientReturning(HttpStatus status, String body, AtomicReference<String> requestedUrl) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
            requestedUrl.set(request.url().toString());
            ClientResponse.Builder response = ClientResponse.create(status)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            return Mono.just(body == null ? response.build() : response.body(body).build());
        });
        return new RunpodClient(builder, new ObjectMapper(), ENDPOINT, "test-key");
    }

    // RunpodClient가 남기는 로그 메시지를 모아 돌려준다
    private List<String> captureRunpodClientLogs(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(RunpodClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void submit_returnsRunpodJobId() {
        AtomicReference<String> url = new AtomicReference<>();
        RunpodClient client = clientReturning(HttpStatus.OK, "{\"id\":\"job-123\",\"status\":\"IN_QUEUE\"}", url);

        String jobId = client.submit(Map.of("action", "generate"));

        assertThat(jobId).isEqualTo("job-123");
        // trailing slash가 없어도 엔드포인트 경로가 유실되지 않아야 한다
        assertThat(url.get()).isEqualTo(ENDPOINT + "/run");
    }

    @Test
    void submit_returnsNullWhenBodyIsEmpty() {
        RunpodClient client = clientReturning(HttpStatus.OK, null, new AtomicReference<>());

        assertThat(client.submit(Map.of("action", "generate"))).isNull();
    }

    @Test
    void submit_logsStatusAndBodyWhenRunpodRejects() {
        RunpodClient client = clientReturning(HttpStatus.CONFLICT, "{\"error\":\"endpoint is updating\"}", new AtomicReference<>());

        List<String> logs = captureRunpodClientLogs(() ->
                assertThatThrownBy(() -> client.submit(Map.of("action", "generate")))
                        .isInstanceOf(WebClientResponseException.Conflict.class));

        assertThat(logs).anySatisfy(message -> assertThat(message)
                .contains("409")
                .contains("endpoint is updating")
                .doesNotContain("test-key"));
    }

    @Test
    void submit_truncatesLongRejectionBody() {
        RunpodClient client = clientReturning(HttpStatus.BAD_REQUEST, "x".repeat(5000), new AtomicReference<>());

        List<String> logs = captureRunpodClientLogs(() ->
                assertThatThrownBy(() -> client.submit(Map.of("action", "generate")))
                        .isInstanceOf(WebClientResponseException.class));

        assertThat(logs).anySatisfy(message -> assertThat(message)
                .contains("...(truncated)")
                .hasSizeLessThan(2000));
    }

    @Test
    void getStatus_parsesDelayAndExecutionTime() {
        AtomicReference<String> url = new AtomicReference<>();
        RunpodClient client = clientReturning(HttpStatus.OK,
                "{\"id\":\"job-123\",\"status\":\"COMPLETED\",\"delayTime\":612345,\"executionTime\":45120,\"output\":{}}", url);

        RunpodClient.JobStatus status = client.getStatus("job-123").orElseThrow();

        assertThat(url.get()).isEqualTo(ENDPOINT + "/status/job-123");
        assertThat(status.isFinished()).isTrue();
        assertThat(status.delayTime()).isEqualTo(612345L);
        assertThat(status.executionTime()).isEqualTo(45120L);
    }

    @Test
    void getStatus_inProgressIsNotFinished() {
        RunpodClient client = clientReturning(HttpStatus.OK,
                "{\"id\":\"job-123\",\"status\":\"IN_PROGRESS\",\"delayTime\":1200}", new AtomicReference<>());

        assertThat(client.getStatus("job-123").orElseThrow().isFinished()).isFalse();
    }

    @Test
    void getStatus_returnsEmptyOnHttpError() {
        RunpodClient client = clientReturning(HttpStatus.NOT_FOUND, "{\"error\":\"not found\"}", new AtomicReference<>());

        assertThat(client.getStatus("expired-job")).isEmpty();
    }
}
