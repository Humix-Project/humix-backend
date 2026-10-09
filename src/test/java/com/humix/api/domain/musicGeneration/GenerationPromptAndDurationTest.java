package com.humix.api.domain.musicGeneration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.humix.api.domain.humming.entity.Humming;
import com.humix.api.domain.humming.repository.HummingRepository;
import com.humix.api.domain.melodyScore.entity.MelodyScore;
import com.humix.api.domain.melodyScore.repository.MelodyScoreRepository;
import com.humix.api.domain.member.entity.Member;
import com.humix.api.domain.member.repository.MemberRepository;
import com.humix.api.domain.musicGeneration.entity.MusicGeneration;
import com.humix.api.domain.musicGeneration.repository.MusicGenerationRepository;
import com.humix.api.domain.musicGeneration.type.GenerationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GenerationPromptAndDurationTest {

    /** AI 서버(RunPod)로 나가는 요청 본문을 실제 호출 없이 가로챈다. */
    @TestConfiguration
    static class CapturingWebClientConfig {
        static final List<String> AI_REQUEST_BODIES = new CopyOnWriteArrayList<>();

        @Bean
        @Primary
        WebClient.Builder capturingWebClientBuilder() {
            return WebClient.builder().exchangeFunction(request -> {
                MockClientHttpRequest captured = new MockClientHttpRequest(request.method(), request.url());
                return request.writeTo(captured, ExchangeStrategies.withDefaults())
                        .then(Mono.defer(captured::getBodyAsString))
                        .doOnNext(AI_REQUEST_BODIES::add)
                        .thenReturn(ClientResponse.create(HttpStatus.OK).build());
            });
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MemberRepository memberRepository;
    @Autowired private HummingRepository hummingRepository;
    @Autowired private MelodyScoreRepository melodyScoreRepository;
    @Autowired private MusicGenerationRepository musicGenerationRepository;

    private String deviceId;
    private String bearer;

    @BeforeEach
    void login() throws Exception {
        CapturingWebClientConfig.AI_REQUEST_BODIES.clear();
        deviceId = UUID.randomUUID().toString(); // member.uuid는 36자 제한
        String body = mockMvc.perform(post("/api/v1/auth/guest-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"device_id\":\"" + deviceId + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        bearer = "Bearer " + objectMapper.readTree(body).at("/result/access_token").asText();
    }

    // ---------- 허밍 저장 API: 30초 초과 거부 ----------

    @Test
    void 허밍_저장_31초면_400_HUMMING4001() throws Exception {
        postJson("/api/v1/upload/humming", "{\"file_key\":\"audio/a.wav\",\"duration_seconds\":31}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HUMMING4001"))
                .andExpect(jsonPath("$.message").value("허밍은 최대 30초까지 가능합니다."));
    }

    @Test
    void 허밍_저장_30초는_허용() throws Exception {
        postJson("/api/v1/upload/humming", "{\"file_key\":\"audio/a.wav\",\"duration_seconds\":30}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.duration_seconds").value(30));
    }

    // ---------- 곡 생성 API: prompt 검증 ----------

    @Test
    void 곡생성_prompt가_500자를_넘으면_400() throws Exception {
        postJson("/api/v1/generation/songs",
                "{\"humming_id\":1,\"title\":\"t\",\"genre\":\"pop\",\"mood\":\"happy\",\"prompt\":\""
                        + "가".repeat(501) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.prompt").exists());
    }

    // ---------- 곡 생성 API: 멜로디 길이 검증 ----------

    @Test
    void 곡생성_멜로디가_30초를_넘으면_400_HUMMING4001이고_AI에_요청하지_않는다() throws Exception {
        Long hummingId = saveHummingWithNotes(
                "[{\"start_time_seconds\":0.0,\"pitch\":60,\"duration_seconds\":30.2}]");

        postJson("/api/v1/generation/songs", generateBody(hummingId, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HUMMING4001"));

        assertThat(CapturingWebClientConfig.AI_REQUEST_BODIES).isEmpty();
    }

    @Test
    void 곡생성_멜로디가_정확히_30점1초에_끝나면_허용() throws Exception {
        // 양자화 오차 0.1초 여유 경계. 부동소수점 합(29.6 + 0.5)이어도 거부되면 안 된다.
        Long hummingId = saveHummingWithNotes(
                "[{\"start_time_seconds\":0.0,\"pitch\":60,\"duration_seconds\":29.6},"
                        + "{\"start_time_seconds\":29.6,\"pitch\":62,\"duration_seconds\":0.5}]");

        postJson("/api/v1/generation/songs", generateBody(hummingId, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON202"));
    }

    // ---------- 곡 생성 API: AI 요청 본문 ----------

    @Test
    void 곡생성_AI_요청에_prompt와_duration_seconds를_담고_엔티티에_저장한다() throws Exception {
        Long hummingId = saveHummingWithNotes(shortNotes());

        String taskId = startGeneration(hummingId, "잔잔한 피아노 위주로");

        JsonNode input = onlyAiRequest().path("input");
        assertThat(input.path("action").asText()).isEqualTo("generate");
        assertThat(input.path("task_id").asText()).isEqualTo(taskId);
        assertThat(input.path("prompt").asText()).isEqualTo("잔잔한 피아노 위주로");
        assertThat(input.path("duration_seconds").asInt()).isEqualTo(30);

        MusicGeneration saved = musicGenerationRepository.findByTaskId(taskId).orElseThrow();
        assertThat(saved.getPrompt()).isEqualTo("잔잔한 피아노 위주로");
        assertThat(saved.getDurationSeconds()).isEqualTo(30.0);
    }

    @Test
    void 곡생성_prompt가_없어도_duration_seconds는_항상_보내고_prompt는_생략한다() throws Exception {
        Long hummingId = saveHummingWithNotes(shortNotes());

        String taskId = startGeneration(hummingId, null);

        JsonNode input = onlyAiRequest().path("input");
        assertThat(input.has("prompt")).isFalse();
        assertThat(input.path("duration_seconds").asInt()).isEqualTo(30);
        assertThat(musicGenerationRepository.findByTaskId(taskId).orElseThrow().getPrompt()).isNull();
    }

    @Test
    void 곡생성_공백_prompt는_없는_것으로_취급한다() throws Exception {
        Long hummingId = saveHummingWithNotes(shortNotes());

        String taskId = startGeneration(hummingId, "   ");

        assertThat(onlyAiRequest().path("input").has("prompt")).isFalse();
        assertThat(musicGenerationRepository.findByTaskId(taskId).orElseThrow().getPrompt()).isNull();
    }

    // ---------- 완료 콜백: 길이 검증 ----------

    @Test
    void 완료콜백_길이가_허용오차_이내면_COMPLETED이고_실제_길이를_저장한다() throws Exception {
        String taskId = startGeneration(saveHummingWithNotes(shortNotes()), null);

        complete(taskId, "https://example.com/a.wav", 29.95).andExpect(status().isOk());

        MusicGeneration result = musicGenerationRepository.findByTaskId(taskId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(result.getResultS3Url()).isEqualTo("https://example.com/a.wav");
        assertThat(result.getDurationSeconds()).isEqualTo(29.95);

        // 내 곡 목록 응답의 duration_seconds는 소수 그대로 내려간다.
        mockMvc.perform(get("/api/v1/me/songs").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.contents[0].duration_seconds").value(29.95));
    }

    @Test
    void 완료콜백_정확히_0점1초_차이는_허용한다() throws Exception {
        String longer = startGeneration(saveHummingWithNotes(shortNotes()), null);
        String shorter = startGeneration(saveHummingWithNotes(shortNotes()), null);

        complete(longer, "https://example.com/l.wav", 30.1).andExpect(status().isOk());
        complete(shorter, "https://example.com/s.wav", 29.9).andExpect(status().isOk());

        assertThat(statusOf(longer)).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(statusOf(shorter)).isEqualTo(GenerationStatus.COMPLETED);
    }

    @Test
    void 완료콜백_길이가_허용오차를_넘으면_FAILED() throws Exception {
        String taskId = startGeneration(saveHummingWithNotes(shortNotes()), null);

        complete(taskId, "https://example.com/a.wav", 29.8).andExpect(status().isOk());

        MusicGeneration result = musicGenerationRepository.findByTaskId(taskId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(GenerationStatus.FAILED);
        assertThat(result.getResultS3Url()).isNull();
    }

    @Test
    void 완료콜백_duration_seconds가_없으면_검증을_건너뛰고_요청한_길이를_유지한다() throws Exception {
        // 아직 duration_seconds를 보내지 않는 구 AI 서버와의 호환 (백엔드 먼저 배포)
        String taskId = startGeneration(saveHummingWithNotes(shortNotes()), null);

        mockMvc.perform(post("/api/v1/internal/tasks/" + taskId + "/completion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generated_audio_url\":\"https://example.com/a.wav\"}"))
                .andExpect(status().isOk());

        MusicGeneration result = musicGenerationRepository.findByTaskId(taskId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(result.getDurationSeconds()).isEqualTo(30.0);
    }

    @Test
    void 완료콜백_실패_통보는_duration_seconds_없이도_FAILED() throws Exception {
        String taskId = startGeneration(saveHummingWithNotes(shortNotes()), null);

        mockMvc.perform(post("/api/v1/internal/tasks/" + taskId + "/completion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generated_audio_url\":\"FAILED\"}"))
                .andExpect(status().isOk());

        assertThat(statusOf(taskId)).isEqualTo(GenerationStatus.FAILED);
    }

    @Test
    void 수정곡_완료콜백은_길이를_비교하지_않고_보고된_길이를_저장한다() throws Exception {
        Long hummingId = saveHummingWithNotes(shortNotes());
        MelodyScore melody = melodyScoreRepository.findByHummingId(hummingId).orElseThrow();
        Member member = memberRepository.findById(deviceId).orElseThrow();
        MusicGeneration parent = musicGenerationRepository.save(MusicGeneration.builder()
                .member(member).melodyScore(melody).name("원본").genre("pop").atmosphere("happy")
                .prompt("원본 프롬프트").durationSeconds(30.0).build());

        String body = postJson("/api/v1/generation/songs/" + parent.getId() + "/modifications",
                "{\"prompt\":\"더 밝게\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON202"))
                .andReturn().getResponse().getContentAsString();
        String taskId = objectMapper.readTree(body).at("/result/task_id").asText();

        complete(taskId, "https://example.com/m.wav", 12.3).andExpect(status().isOk());

        MusicGeneration modified = musicGenerationRepository.findByTaskId(taskId).orElseThrow();
        assertThat(modified.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(modified.getDurationSeconds()).isEqualTo(12.3);
        assertThat(modified.getPrompt()).isEqualTo("원본 프롬프트"); // 원본의 스타일 프롬프트를 이어받는다
    }

    // ---------- helpers ----------

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url)
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions complete(String taskId, String audioUrl, double durationSeconds) throws Exception {
        return mockMvc.perform(post("/api/v1/internal/tasks/" + taskId + "/completion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"generated_audio_url\":\"" + audioUrl + "\",\"duration_seconds\":" + durationSeconds + "}"));
    }

    private String generateBody(Long hummingId, String prompt) throws Exception {
        StringBuilder json = new StringBuilder("{\"humming_id\":" + hummingId
                + ",\"title\":\"t\",\"genre\":\"pop\",\"mood\":\"happy\"");
        if (prompt != null) {
            json.append(",\"prompt\":").append(objectMapper.writeValueAsString(prompt));
        }
        return json.append("}").toString();
    }

    private String startGeneration(Long hummingId, String prompt) throws Exception {
        String body = postJson("/api/v1/generation/songs", generateBody(hummingId, prompt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON202"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/result/task_id").asText();
    }

    private Long saveHummingWithNotes(String notesJson) {
        Member member = memberRepository.findById(deviceId).orElseThrow();
        Humming humming = hummingRepository.save(Humming.builder()
                .member(member).s3FileUrl("https://example.com/humming.wav").durationSeconds(10).build());
        melodyScoreRepository.save(MelodyScore.builder().humming(humming).notesData(notesJson).build());
        return humming.getId();
    }

    private String shortNotes() {
        return "[{\"start_time_seconds\":0.0,\"pitch\":60,\"duration_seconds\":1.0}]";
    }

    private JsonNode onlyAiRequest() throws Exception {
        assertThat(CapturingWebClientConfig.AI_REQUEST_BODIES).hasSize(1);
        return objectMapper.readTree(CapturingWebClientConfig.AI_REQUEST_BODIES.get(0));
    }

    private GenerationStatus statusOf(String taskId) {
        return musicGenerationRepository.findByTaskId(taskId).orElseThrow().getStatus();
    }
}
