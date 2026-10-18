package com.humix.api.domain.humming;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.humix.api.domain.humming.entity.Humming;
import com.humix.api.domain.humming.repository.HummingRepository;
import com.humix.api.domain.member.entity.Member;
import com.humix.api.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HummingVectorStartTimeTest {

    /** 멜로디 추출 서버 응답을 테스트마다 정한 값으로 돌려준다. 추출 서버는 start_time_seconds를 항상 0.0으로 준다. */
    @TestConfiguration
    static class StubExtractorConfig {
        static final AtomicReference<String> EXTRACT_RESPONSE = new AtomicReference<>("[]");

        @Bean
        @Primary
        WebClient.Builder stubExtractorWebClientBuilder() {
            return WebClient.builder().exchangeFunction(request -> Mono.just(
                    ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body(EXTRACT_RESPONSE.get())
                            .build()));
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MemberRepository memberRepository;
    @Autowired private HummingRepository hummingRepository;

    private String deviceId;
    private String bearer;

    @BeforeEach
    void login() throws Exception {
        deviceId = UUID.randomUUID().toString(); // member.uuid는 36자 제한
        String body = mockMvc.perform(post("/api/v1/auth/guest-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"device_id\":\"" + deviceId + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        bearer = "Bearer " + objectMapper.readTree(body).at("/result/access_token").asText();
    }

    @Test
    void 벡터화_0점01초_단위_음길이는_앞_음이_끝나는_시점에서_다음_음이_시작한다() throws Exception {
        // 실제 녹음에서 0.1초 반올림 시 겹쳤던 구간의 음길이 (약 0.016초 프레임 기준, 0.01초 단위)
        List<Double> durations = List.of(0.05, 0.06, 0.02, 0.11, 0.10, 0.13, 0.14);

        List<Double> starts = startTimesOf(convert(durations));

        assertThat(starts).containsExactly(0.0, 0.05, 0.11, 0.13, 0.24, 0.34, 0.47);
    }

    @Test
    void 벡터화_음표가_많아도_부동소수점_오차가_쌓이지_않고_겹치지_않는다() throws Exception {
        // 0.03초 × 1000개: 단순 누적합은 29.97에서 미세하게 벗어난다
        List<Double> durations = new ArrayList<>(Collections.nCopies(1000, 0.03));

        JsonNode notes = convert(durations);
        List<Double> starts = startTimesOf(notes);

        for (int i = 0; i < starts.size(); i++) {
            assertThat(starts.get(i)).isEqualTo(i * 3 / 100.0);
        }
        for (int i = 1; i < notes.size(); i++) {
            double previousEnd = notes.get(i - 1).path("start_time_seconds").asDouble()
                    + notes.get(i - 1).path("duration_seconds").asDouble();
            assertThat(notes.get(i).path("start_time_seconds").asDouble()).isGreaterThanOrEqualTo(previousEnd - 1e-9);
        }
    }

    private JsonNode convert(List<Double> durations) throws Exception {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < durations.size(); i++) {
            if (i > 0) json.append(',');
            json.append("{\"start_time_seconds\":0.0,\"pitch\":60,\"duration_seconds\":").append(durations.get(i)).append('}');
        }
        StubExtractorConfig.EXTRACT_RESPONSE.set(json.append(']').toString());

        String body = mockMvc.perform(post("/api/v1/hummings/" + saveHumming() + "/vectors")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/result/notes");
    }

    private List<Double> startTimesOf(JsonNode notes) {
        List<Double> starts = new ArrayList<>();
        notes.forEach(note -> starts.add(note.path("start_time_seconds").asDouble()));
        return starts;
    }

    private Long saveHumming() {
        Member member = memberRepository.findById(deviceId).orElseThrow();
        return hummingRepository.save(Humming.builder()
                .member(member).s3FileUrl("https://example.com/humming.wav").durationSeconds(10).build()).getId();
    }
}
