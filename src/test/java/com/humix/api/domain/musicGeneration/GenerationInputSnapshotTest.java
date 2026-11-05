package com.humix.api.domain.musicGeneration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** #58 버전별 생성 입력(확장 멜로디·허밍 끝 시점·텍스트 조건): 완료 콜백에서 버전마다 저장하고, 구간 재생성 때 수정 대상 버전의 값을 보낸다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GenerationInputSnapshotTest {

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

    // 사용자가 편집하는 허밍 원본 (melody_score.notes_data)
    private static final String HUMMING_NOTES = "[{\"start_time_seconds\":0.0,\"pitch\":60,\"duration_seconds\":1.0},"
            + "{\"start_time_seconds\":1.0,\"pitch\":0,\"duration_seconds\":0.5}]";

    // AI가 확장·보정까지 끝낸 곡 전체 멜로디. 시점이 소수 둘째 자리라 반올림 없이 그대로 저장돼야 한다.
    private static final String MELODY_A = "[{\"pitch\":0,\"onset_seconds\":0.0,\"duration_seconds\":1.06},"
            + "{\"pitch\":62,\"onset_seconds\":1.06,\"duration_seconds\":0.21},"
            + "{\"pitch\":64,\"onset_seconds\":12.12,\"duration_seconds\":17.88}]";

    // A에서 확장 구간(18–28초) 멜로디만 다시 뽑은 결과
    private static final String MELODY_B = "[{\"pitch\":0,\"onset_seconds\":0.0,\"duration_seconds\":1.06},"
            + "{\"pitch\":62,\"onset_seconds\":1.06,\"duration_seconds\":0.21},"
            + "{\"pitch\":64,\"onset_seconds\":12.12,\"duration_seconds\":5.88},"
            + "{\"pitch\":67,\"onset_seconds\":18.0,\"duration_seconds\":12.0}]";

    private static final String TEXT_A = "upbeat k-pop with bright synths and punchy drums, 120 bpm";
    private static final String TEXT_B = "upbeat k-pop with bright synths, a rising lead melody in the second half, 120 bpm";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MemberRepository memberRepository;
    @Autowired private HummingRepository hummingRepository;
    @Autowired private MelodyScoreRepository melodyScoreRepository;
    @Autowired private MusicGenerationRepository musicGenerationRepository;

    private String bearer;
    private Humming humming;

    @BeforeEach
    void setUp() throws Exception {
        CapturingWebClientConfig.AI_REQUEST_BODIES.clear();
        // 테스트끼리 데이터가 섞이지 않도록 매번 새 사용자로 시작한다 (member.uuid는 36자)
        String deviceId = UUID.randomUUID().toString();
        String body = mockMvc.perform(post("/api/v1/auth/guest-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"device_id\":\"" + deviceId + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        bearer = "Bearer " + objectMapper.readTree(body).at("/result/access_token").asText();

        Member member = memberRepository.findById(deviceId).orElseThrow();
        humming = hummingRepository.save(Humming.builder()
                .member(member).s3FileUrl("https://example.com/humming.wav").durationSeconds(10).build());
        melodyScoreRepository.save(MelodyScore.builder().humming(humming).notesData(HUMMING_NOTES).build());
    }

    // ---------- 완료 콜백: 버전마다 저장 ----------

    @Test
    void 최초_생성_완료콜백의_생성_입력을_그_버전에_그대로_저장한다() throws Exception {
        String taskId = startGeneration(null);

        complete(taskId, completionBody(MELODY_A, 12.12, TEXT_A)).andExpect(status().isOk());

        MusicGeneration v1 = find(taskId);
        assertThat(v1.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(objectMapper.readTree(v1.getExtendedMelody())).isEqualTo(objectMapper.readTree(MELODY_A));
        assertThat(v1.getHummingEndSeconds()).isEqualTo(12.12);
        assertThat(v1.getTextCondition()).isEqualTo(TEXT_A);
        // 사용자가 편집하는 허밍 원본은 바꾸지 않는다
        assertThat(melodyScoreRepository.findByHummingId(humming.getId()).orElseThrow().getNotesData())
                .isEqualTo(HUMMING_NOTES);
    }

    @Test
    void 구간_재생성_완료콜백은_새_버전에_자기_값을_저장하고_수정_대상_버전의_값은_그대로_둔다() throws Exception {
        MusicGeneration v1 = generateCompleted(MELODY_A, 12.12, TEXT_A);

        String taskId = startModification(v1, null, 18, 28);
        // 부모 값을 미리 복사하지 않는다. 콜백으로 받은 값만 저장한다.
        MusicGeneration pending = find(taskId);
        assertThat(pending.getExtendedMelody()).isNull();
        assertThat(pending.getHummingEndSeconds()).isNull();
        assertThat(pending.getTextCondition()).isNull();

        complete(taskId, completionBody(MELODY_B, 12.12, TEXT_B)).andExpect(status().isOk());

        MusicGeneration v2 = find(taskId);
        assertThat(v2.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(objectMapper.readTree(v2.getExtendedMelody())).isEqualTo(objectMapper.readTree(MELODY_B));
        assertThat(v2.getHummingEndSeconds()).isEqualTo(12.12);
        assertThat(v2.getTextCondition()).isEqualTo(TEXT_B);

        MusicGeneration untouched = musicGenerationRepository.findById(v1.getId()).orElseThrow();
        assertThat(objectMapper.readTree(untouched.getExtendedMelody())).isEqualTo(objectMapper.readTree(MELODY_A));
        assertThat(untouched.getTextCondition()).isEqualTo(TEXT_A);
    }

    @Test
    void 완료콜백에_생성_입력이_없으면_NULL로_두고_완료는_정상_처리한다() throws Exception {
        // 아직 새 필드를 보내지 않는 AI 서버와의 호환 (백엔드 먼저 배포)
        String taskId = startGeneration(null);

        complete(taskId, completionBody(null, null, null)).andExpect(status().isOk());

        MusicGeneration v1 = find(taskId);
        assertThat(v1.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(v1.getExtendedMelody()).isNull();
        assertThat(v1.getHummingEndSeconds()).isNull();
        assertThat(v1.getTextCondition()).isNull();
    }

    @Test
    void 실패한_작업에는_생성_입력을_저장하지_않는다() throws Exception {
        String notified = startGeneration(null);
        String mismatched = startGeneration(null);

        complete(notified, "{\"generated_audio_url\":\"FAILED\"}").andExpect(status().isOk());
        ObjectNode wrongLength = completionJson(MELODY_A, 12.12, TEXT_A);
        wrongLength.put("duration_seconds", 12.3);
        complete(mismatched, wrongLength.toString()).andExpect(status().isOk());

        for (String taskId : List.of(notified, mismatched)) {
            MusicGeneration failed = find(taskId);
            assertThat(failed.getStatus()).isEqualTo(GenerationStatus.FAILED);
            assertThat(failed.getExtendedMelody()).isNull();
            assertThat(failed.getHummingEndSeconds()).isNull();
            assertThat(failed.getTextCondition()).isNull();
        }
    }

    @Test
    void 빈_멜로디_배열은_저장하지_않아_다음_재생성이_melody_score로_대체된다() throws Exception {
        MusicGeneration v1 = generateCompleted("[]", 12.12, TEXT_A);
        assertThat(v1.getExtendedMelody()).isNull();

        startModification(v1, null, 0, 10);

        assertThat(onlyAiRequest().path("input").path("melody_vectors")).hasSize(2);
    }

    @Test
    void text_condition이_1000자를_넘으면_저장하지_않고_완료는_정상_처리한다() throws Exception {
        MusicGeneration fits = generateCompleted(MELODY_A, 12.12, "a".repeat(1000));
        MusicGeneration tooLong = generateCompleted(MELODY_A, 12.12, "a".repeat(1001));

        assertThat(fits.getTextCondition()).hasSize(1000);
        assertThat(tooLong.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(tooLong.getTextCondition()).isNull();
        // 나머지 값은 그대로 저장한다
        assertThat(tooLong.getExtendedMelody()).isNotNull();
        assertThat(tooLong.getHummingEndSeconds()).isEqualTo(12.12);
    }

    // ---------- 구간 재생성 요청: 수정 대상 버전의 값 전달 ----------

    @Test
    void 수정_대상_버전의_확장_멜로디와_허밍_끝_시점_텍스트_조건을_보낸다() throws Exception {
        MusicGeneration v1 = generateCompleted(MELODY_A, 12.12, TEXT_A);

        startModification(v1, null, 18, 28);

        JsonNode input = onlyAiRequest().path("input");
        assertThat(input.path("action").asText()).isEqualTo("modify");
        assertThat(input.path("melody_vectors")).isEqualTo(objectMapper.readTree(MELODY_A));
        assertThat(input.path("humming_end_seconds").asDouble()).isEqualTo(12.12);
        assertThat(input.path("text_condition").asText()).isEqualTo(TEXT_A);
    }

    @Test
    void 생성_입력이_없는_기존_곡은_melody_score를_보내고_허밍_끝_시점과_텍스트_조건은_생략한다() throws Exception {
        MusicGeneration legacy = generateCompleted(null, null, null);

        startModification(legacy, null, 0, 10);

        JsonNode input = onlyAiRequest().path("input");
        JsonNode melodyVectors = input.path("melody_vectors");
        assertThat(melodyVectors).hasSize(2);
        assertThat(melodyVectors.get(0).path("pitch").asInt()).isEqualTo(60);
        assertThat(melodyVectors.get(1).path("pitch").asInt()).isZero();
        assertThat(melodyVectors.get(1).path("onset_seconds").asDouble()).isEqualTo(1.0);
        assertThat(input.has("humming_end_seconds")).isFalse();
        assertThat(input.has("text_condition")).isFalse();
    }

    @Test
    void edit_prompt는_이번_요청에_직접_입력한_프롬프트만_보낸다() throws Exception {
        MusicGeneration v1 = generateCompleted("로파이 비트", MELODY_A, 12.12, TEXT_A);

        String typed = startModification(v1, "더 밝고 경쾌하게", 0, 10);
        JsonNode input = onlyAiRequest().path("input");
        assertThat(input.path("edit_prompt").asText()).isEqualTo("더 밝고 경쾌하게");
        assertThat(input.path("prompt").asText()).isEqualTo("더 밝고 경쾌하게");
        completeAndClear(typed);

        // 생략하거나 공백이면 prompt는 지금처럼 부모 값으로 채우되, edit_prompt는 보내지 않는다
        String omitted = startModification(v1, null, 0, 10);
        input = onlyAiRequest().path("input");
        assertThat(input.has("edit_prompt")).isFalse();
        assertThat(input.path("prompt").asText()).isEqualTo("로파이 비트");
        completeAndClear(omitted);

        startModification(v1, "   ", 0, 10);
        input = onlyAiRequest().path("input");
        assertThat(input.has("edit_prompt")).isFalse();
        assertThat(input.path("prompt").asText()).isEqualTo("로파이 비트");
    }

    @Test
    void 재생성의_재생성은_root가_아니라_수정_대상_버전의_값을_보낸다() throws Exception {
        // v1(root, A) → v2: v1의 18–28초를 재생성하며 멜로디를 다시 뽑음(B) → v3: v2의 5–10초를 "더 밝게"
        MusicGeneration v1 = generateCompleted(MELODY_A, 12.12, TEXT_A);
        String v2Task = startModification(v1, null, 18, 28);
        // 어느 버전의 값이 가는지 구분하려고 허밍 끝 시점도 다르게 둔다
        complete(v2Task, completionBody(MELODY_B, 12.5, TEXT_B)).andExpect(status().isOk());
        MusicGeneration v2 = find(v2Task);
        CapturingWebClientConfig.AI_REQUEST_BODIES.clear();

        startModification(v2, "더 밝게", 5, 10);

        JsonNode input = onlyAiRequest().path("input");
        assertThat(input.path("melody_vectors")).isEqualTo(objectMapper.readTree(MELODY_B));
        assertThat(input.path("humming_end_seconds").asDouble()).isEqualTo(12.5);
        assertThat(input.path("text_condition").asText()).isEqualTo(TEXT_B);
        assertThat(input.path("edit_prompt").asText()).isEqualTo("더 밝게");
        assertThat(input.path("source_audio_url").asText()).isEqualTo(v2.getResultS3Url());
    }

    @Test
    void 곡을_만든_뒤_편집기에서_멜로디를_고쳐도_재생성_멜로디는_바뀌지_않는다() throws Exception {
        MusicGeneration v1 = generateCompleted(MELODY_A, 12.12, TEXT_A);

        mockMvc.perform(put("/api/v1/hummings/{id}/vectors", humming.getId())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":[{\"start_time_seconds\":0.0,\"pitch\":72,\"duration_seconds\":2.0}]}"))
                .andExpect(status().isOk());
        assertThat(melodyScoreRepository.findByHummingId(humming.getId()).orElseThrow().getNotesData())
                .contains("72");

        startModification(v1, null, 0, 10);

        assertThat(onlyAiRequest().path("input").path("melody_vectors")).isEqualTo(objectMapper.readTree(MELODY_A));
    }

    // ---------- helpers ----------

    private String startGeneration(String prompt) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("humming_id", humming.getId());
        body.put("title", "t");
        body.put("genre", "kpop");
        body.put("mood", "dynamic");
        if (prompt != null) {
            body.put("prompt", prompt);
        }
        String response = mockMvc.perform(post("/api/v1/generation/songs")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("COMMON202"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/result/task_id").asText();
    }

    private MusicGeneration generateCompleted(String melodyJson, Double hummingEnd, String textCondition) throws Exception {
        return generateCompleted(null, melodyJson, hummingEnd, textCondition);
    }

    // 곡 생성 → 완료 콜백까지 마친 버전. 이후 재생성 요청만 남도록 AI 요청 기록을 비운다.
    private MusicGeneration generateCompleted(String prompt, String melodyJson, Double hummingEnd, String textCondition)
            throws Exception {
        String taskId = startGeneration(prompt);
        complete(taskId, completionBody(melodyJson, hummingEnd, textCondition)).andExpect(status().isOk());
        CapturingWebClientConfig.AI_REQUEST_BODIES.clear();
        return find(taskId);
    }

    private String startModification(MusicGeneration target, String prompt, int start, int end) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        if (prompt != null) {
            body.put("prompt", prompt);
        }
        body.put("start_time_sec", start);
        body.put("end_time_sec", end);
        String response = mockMvc.perform(post("/api/v1/generation/songs/{id}/modifications", target.getId())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/result/task_id").asText();
    }

    // 같은 곡에 다음 재생성을 요청할 수 있도록 진행 중인 작업을 끝낸다
    private void completeAndClear(String taskId) throws Exception {
        complete(taskId, completionBody(null, null, null)).andExpect(status().isOk());
        CapturingWebClientConfig.AI_REQUEST_BODIES.clear();
    }

    private ResultActions complete(String taskId, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/internal/tasks/" + taskId + "/completion")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private String completionBody(String melodyJson, Double hummingEnd, String textCondition) throws Exception {
        return completionJson(melodyJson, hummingEnd, textCondition).toString();
    }

    // null인 필드는 넣지 않는다 (AI 서버가 보내지 않은 경우)
    private ObjectNode completionJson(String melodyJson, Double hummingEnd, String textCondition) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("generated_audio_url", "https://example.com/" + UUID.randomUUID() + ".wav");
        body.put("duration_seconds", 30.0);
        if (melodyJson != null) {
            body.set("extended_melody_vectors", objectMapper.readTree(melodyJson));
        }
        if (hummingEnd != null) {
            body.put("humming_end_seconds", hummingEnd);
        }
        if (textCondition != null) {
            body.put("text_condition", textCondition);
        }
        return body;
    }

    private MusicGeneration find(String taskId) {
        return musicGenerationRepository.findByTaskId(taskId).orElseThrow();
    }

    private JsonNode onlyAiRequest() throws Exception {
        assertThat(CapturingWebClientConfig.AI_REQUEST_BODIES).hasSize(1);
        return objectMapper.readTree(CapturingWebClientConfig.AI_REQUEST_BODIES.get(0));
    }
}
