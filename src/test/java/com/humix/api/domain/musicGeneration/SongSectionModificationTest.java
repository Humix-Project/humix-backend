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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** #27 구간 재생성 API: 요청 검증, AI 요청 본문, 동시 작업 차단, 완료 콜백 길이 검증. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SongSectionModificationTest {

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

    private static final String SOURCE_URL = "https://example.com/source.wav";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MemberRepository memberRepository;
    @Autowired private HummingRepository hummingRepository;
    @Autowired private MelodyScoreRepository melodyScoreRepository;
    @Autowired private MusicGenerationRepository musicGenerationRepository;

    private String bearer;
    private Member member;
    private MelodyScore melody;
    private MusicGeneration source; // 원본(root) 곡: 완료, 30초, prompt "원본 프롬프트"

    @BeforeEach
    void setUp() throws Exception {
        CapturingWebClientConfig.AI_REQUEST_BODIES.clear();
        // 테스트끼리 데이터가 섞이지 않도록 매번 새 사용자로 시작한다 (member.uuid는 36자)
        String deviceId = UUID.randomUUID().toString();
        bearer = login(deviceId);
        member = memberRepository.findById(deviceId).orElseThrow();
        Humming humming = hummingRepository.save(Humming.builder()
                .member(member).s3FileUrl("https://example.com/humming.wav").durationSeconds(10).build());
        melody = melodyScoreRepository.save(MelodyScore.builder()
                .humming(humming)
                .notesData("[{\"start_time_seconds\":0.0,\"pitch\":60,\"duration_seconds\":1.0},"
                        + "{\"start_time_seconds\":1.0,\"pitch\":0,\"duration_seconds\":0.5}]")
                .build());
        source = completed(member, null, SOURCE_URL, 30.0, "원본 프롬프트");
    }

    // ---------- 새 버전 생성 ----------

    @Test
    void 구간_재생성은_새_버전을_PROCESSING으로_만들고_원곡은_그대로_둔다() throws Exception {
        String taskId = startModification(source, "더 밝게", 10, 20);

        MusicGeneration created = musicGenerationRepository.findByTaskId(taskId).orElseThrow();
        assertThat(created.getId()).isNotEqualTo(source.getId());
        assertThat(created.getStatus()).isEqualTo(GenerationStatus.PROCESSING);
        assertThat(created.getRootGenerationId()).isEqualTo(source.getId());
        assertThat(created.getStartTimeSec()).isEqualTo(10);
        assertThat(created.getEndTimeSec()).isEqualTo(20);
        assertThat(created.getPrompt()).isEqualTo("더 밝게");
        assertThat(created.getGenre()).isEqualTo("pop");
        assertThat(created.getAtmosphere()).isEqualTo("happy");
        assertThat(created.getDurationSeconds()).isEqualTo(30.0); // 결과는 병합된 전체 곡이므로 원곡 길이

        MusicGeneration untouched = musicGenerationRepository.findById(source.getId()).orElseThrow();
        assertThat(untouched.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(untouched.getResultS3Url()).isEqualTo(SOURCE_URL);
        assertThat(untouched.getDeletedAt()).isNull();
        assertThat(untouched.getStartTimeSec()).isNull();
        assertThat(untouched.getEndTimeSec()).isNull();
    }

    @Test
    void AI_요청에_원곡_URL과_구간_장르_분위기_전체_멜로디를_담고_duration_seconds는_보내지_않는다() throws Exception {
        String taskId = startModification(source, "더 밝게", 10, 20);

        JsonNode input = onlyAiRequest().path("input");
        assertThat(input.path("action").asText()).isEqualTo("modify");
        assertThat(input.path("task_id").asText()).isEqualTo(taskId);
        assertThat(input.path("source_audio_url").asText()).isEqualTo(SOURCE_URL);
        assertThat(input.path("section_start_seconds").asInt()).isEqualTo(10);
        assertThat(input.path("section_end_seconds").asInt()).isEqualTo(20);
        assertThat(input.path("prompt").asText()).isEqualTo("더 밝게");
        assertThat(input.path("genre").asText()).isEqualTo("pop");
        assertThat(input.path("mood").asText()).isEqualTo("happy");
        assertThat(input.path("callback_url").asText()).endsWith("/api/v1/internal/tasks/" + taskId + "/completion");
        assertThat(input.has("duration_seconds")).isFalse();

        // 구간과 무관하게 곡 전체 분량의 멜로디를 원곡 기준 시점 그대로 보낸다 (쉼표 pitch 0 포함)
        JsonNode melodyVectors = input.path("melody_vectors");
        assertThat(melodyVectors).hasSize(2);
        assertThat(melodyVectors.get(1).path("pitch").asInt()).isZero();
        assertThat(melodyVectors.get(1).path("onset_seconds").asDouble()).isEqualTo(1.0);

        // 결과는 원곡 키를 덮어쓰지 않도록 요청마다 새로 발급한 presigned URL로 올린다
        String presignedUrl = input.path("presigned_url").asText();
        assertThat(presignedUrl).contains("_variation.wav");
        assertThat(presignedUrl).doesNotContain("source.wav");
    }

    @Test
    void prompt를_생략하거나_공백이면_대상_버전의_prompt를_이어받는다() throws Exception {
        String omitted = startModification(source, null, 0, 10);
        assertThat(onlyAiRequest().path("input").path("prompt").asText()).isEqualTo("원본 프롬프트");
        assertThat(musicGenerationRepository.findByTaskId(omitted).orElseThrow().getPrompt()).isEqualTo("원본 프롬프트");

        // 진행 중 작업이 끝나야 같은 곡에 다시 요청할 수 있다
        complete(omitted, "https://example.com/v2.wav", 30.0).andExpect(status().isOk());
        CapturingWebClientConfig.AI_REQUEST_BODIES.clear();

        String blank = startModification(source, "   ", 0, 10);
        assertThat(onlyAiRequest().path("input").path("prompt").asText()).isEqualTo("원본 프롬프트");
        assertThat(musicGenerationRepository.findByTaskId(blank).orElseThrow().getPrompt()).isEqualTo("원본 프롬프트");
    }

    @Test
    void 대상_버전에도_prompt가_없고_입력도_없으면_AI_요청에서_prompt를_생략한다() throws Exception {
        MusicGeneration noPrompt = completed(member, null, "https://example.com/np.wav", 30.0, null);

        String taskId = startModification(noPrompt, null, 0, 10);

        assertThat(onlyAiRequest().path("input").has("prompt")).isFalse();
        assertThat(musicGenerationRepository.findByTaskId(taskId).orElseThrow().getPrompt()).isNull();
    }

    @Test
    void 수정본을_대상으로_다시_재생성하면_그_버전의_URL을_소스로_쓰고_root는_유지한다() throws Exception {
        MusicGeneration v2 = completed(member, source, "https://example.com/v2.wav", 30.0, "v2 프롬프트");

        String taskId = startModification(v2, null, 5, 15);

        JsonNode input = onlyAiRequest().path("input");
        assertThat(input.path("source_audio_url").asText()).isEqualTo("https://example.com/v2.wav");
        assertThat(input.path("prompt").asText()).isEqualTo("v2 프롬프트");
        MusicGeneration v3 = musicGenerationRepository.findByTaskId(taskId).orElseThrow();
        assertThat(v3.getRootGenerationId()).isEqualTo(source.getId());
    }

    // ---------- 구간 검증 ----------

    @ParameterizedTest(name = "{0}~{1}초는 허용")
    @CsvSource({"0,5", "25,30", "0,30", "10,20"})
    void 허용되는_구간(int start, int end) throws Exception {
        MusicGeneration target = completed(member, null, "https://example.com/" + UUID.randomUUID() + ".wav", 30.0, null);

        modify(target.getId(), null, start, end)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON202"));
    }

    @ParameterizedTest(name = "{0}~{1}초는 400")
    @CsvSource({"-1,10", "10,14", "10,10", "20,10", "0,31", "26,31"})
    void 잘못된_구간은_400_GENERATION4002이고_AI에_요청하지_않는다(int start, int end) throws Exception {
        modify(source.getId(), null, start, end)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GENERATION4002"));

        assertThat(CapturingWebClientConfig.AI_REQUEST_BODIES).isEmpty();
        assertThat(processingIn(source)).isEmpty();
    }

    @Test
    void 곡_길이가_소수여도_0점1초_이내면_끝_30초를_허용하고_넘으면_거부한다() throws Exception {
        MusicGeneration slightlyShort = completed(member, null, "https://example.com/a.wav", 29.95, null);
        MusicGeneration tooShort = completed(member, null, "https://example.com/b.wav", 29.8, null);

        modify(slightlyShort.getId(), null, 0, 30).andExpect(status().isOk());
        modify(tooShort.getId(), null, 0, 30)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GENERATION4002"));
    }

    @Test
    void 구간은_수정_대상_버전의_길이_기준으로_검증한다() throws Exception {
        MusicGeneration shorter = completed(member, source, "https://example.com/short.wav", 20.0, null);

        modify(shorter.getId(), null, 10, 25)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GENERATION4002"));
        modify(shorter.getId(), null, 10, 20).andExpect(status().isOk());
    }

    @Test
    void 구간이_없으면_400이고_prompt가_500자를_넘어도_400() throws Exception {
        mockMvc.perform(post("/api/v1/generation/songs/{id}/modifications", source.getId())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"더 밝게\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.start_time_sec").exists())
                .andExpect(jsonPath("$.result.end_time_sec").exists());

        modify(source.getId(), "가".repeat(501), 0, 10)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.prompt").exists());

        modify(source.getId(), "가".repeat(500), 0, 10).andExpect(status().isOk());
    }

    // ---------- 동시 작업 차단 ----------

    @Test
    void 같은_root에_진행중인_작업이_있으면_409이고_AI에_요청하지_않는다() throws Exception {
        startModification(source, null, 0, 10);
        CapturingWebClientConfig.AI_REQUEST_BODIES.clear();

        modify(source.getId(), null, 10, 20)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GENERATION4001"));

        assertThat(CapturingWebClientConfig.AI_REQUEST_BODIES).isEmpty();
        assertThat(processingIn(source)).hasSize(1);
    }

    @Test
    void 진행중인_작업이_끝나면_다시_요청할_수_있다() throws Exception {
        String first = startModification(source, null, 0, 10);
        complete(first, "https://example.com/v2.wav", 30.0).andExpect(status().isOk());

        modify(source.getId(), null, 10, 20).andExpect(status().isOk());
    }

    @Test
    void 다른_곡의_진행중인_작업은_막지_않는다() throws Exception {
        MusicGeneration other = completed(member, null, "https://example.com/other.wav", 30.0, null);
        startModification(other, null, 0, 10);

        modify(source.getId(), null, 0, 10).andExpect(status().isOk());
    }

    // ---------- 대상 버전 검증 ----------

    @Test
    void 사용할_수_없는_대상은_404_GENERATION4000이고_AI에_요청하지_않는다() throws Exception {
        MusicGeneration failed = pending(member, source, GenerationStatus.FAILED);
        MusicGeneration processing = pending(member, source, GenerationStatus.PROCESSING);
        MusicGeneration noUrl = completed(member, source, null, 30.0, null);
        Member other = memberRepository.save(Member.builder().uuid(UUID.randomUUID().toString()).build());
        MusicGeneration othersSong = completed(other, null, "https://example.com/others.wav", 30.0, null);

        for (Long id : List.of(failed.getId(), processing.getId(), noUrl.getId(), othersSong.getId(), 999_999L)) {
            modify(id, null, 0, 10)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("GENERATION4000"));
        }

        assertThat(CapturingWebClientConfig.AI_REQUEST_BODIES).isEmpty();
    }

    @Test
    void 되돌리기로_삭제된_버전은_대상이_될_수_없다() throws Exception {
        MusicGeneration v2 = completed(member, source, "https://example.com/v2.wav", 30.0, null);
        mockMvc.perform(post("/api/v1/generation/songs/{id}/revert", source.getId()).header("Authorization", bearer))
                .andExpect(status().isOk());

        modify(v2.getId(), null, 0, 10)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GENERATION4000"));
    }

    @Test
    void 로그인_없이는_401() throws Exception {
        mockMvc.perform(post("/api/v1/generation/songs/{id}/modifications", source.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"start_time_sec\":0,\"end_time_sec\":10}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 완료 콜백 ----------

    @Test
    void 완료콜백_원곡_길이와_허용오차_이내면_COMPLETED이고_버전_목록에_구간_정보와_함께_추가된다() throws Exception {
        String taskId = startModification(source, "더 밝게", 10, 20);

        complete(taskId, "https://example.com/v2.wav", 30.05).andExpect(status().isOk());

        MusicGeneration result = musicGenerationRepository.findByTaskId(taskId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(result.getResultS3Url()).isEqualTo("https://example.com/v2.wav"); // 병합된 전체 곡
        assertThat(result.getDurationSeconds()).isEqualTo(30.05);

        versions(source.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.versions.length()").value(2))
                .andExpect(jsonPath("$.result.versions[0].generation_id").value(source.getId()))
                .andExpect(jsonPath("$.result.versions[0].prompt").value("원본 프롬프트"))
                .andExpect(jsonPath("$.result.versions[0].start_time_sec", nullValue()))
                .andExpect(jsonPath("$.result.versions[0].end_time_sec", nullValue()))
                .andExpect(jsonPath("$.result.versions[1].generation_id").value(result.getId()))
                .andExpect(jsonPath("$.result.versions[1].version_no").value(2))
                .andExpect(jsonPath("$.result.versions[1].prompt").value("더 밝게"))
                .andExpect(jsonPath("$.result.versions[1].start_time_sec").value(10))
                .andExpect(jsonPath("$.result.versions[1].end_time_sec").value(20))
                .andExpect(jsonPath("$.result.versions[1].audio_url").value("https://example.com/v2.wav"));
    }

    @Test
    void 완료콜백_정확히_0점1초_차이는_허용한다() throws Exception {
        String longer = startModification(completed(member, null, "https://example.com/l.wav", 30.0, null), null, 0, 10);
        String shorter = startModification(completed(member, null, "https://example.com/s.wav", 30.0, null), null, 0, 10);

        complete(longer, "https://example.com/l2.wav", 30.1).andExpect(status().isOk());
        complete(shorter, "https://example.com/s2.wav", 29.9).andExpect(status().isOk());

        assertThat(statusOf(longer)).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(statusOf(shorter)).isEqualTo(GenerationStatus.COMPLETED);
    }

    @Test
    void 완료콜백_길이가_원곡과_허용오차를_넘으면_FAILED이고_원곡만_남는다() throws Exception {
        String taskId = startModification(source, null, 10, 20);

        complete(taskId, "https://example.com/v2.wav", 12.3).andExpect(status().isOk());

        MusicGeneration result = musicGenerationRepository.findByTaskId(taskId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(GenerationStatus.FAILED);
        assertThat(result.getResultS3Url()).isNull();

        versions(source.getId())
                .andExpect(jsonPath("$.result.versions.length()").value(1))
                .andExpect(jsonPath("$.result.versions[0].generation_id").value(source.getId()));
        MusicGeneration untouched = musicGenerationRepository.findById(source.getId()).orElseThrow();
        assertThat(untouched.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(untouched.getResultS3Url()).isEqualTo(SOURCE_URL);
    }

    @Test
    void 완료콜백_duration_seconds가_없으면_검증을_건너뛰고_원곡_길이를_유지한다() throws Exception {
        String taskId = startModification(source, null, 10, 20);

        mockMvc.perform(post("/api/v1/internal/tasks/" + taskId + "/completion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generated_audio_url\":\"https://example.com/v2.wav\"}"))
                .andExpect(status().isOk());

        MusicGeneration result = musicGenerationRepository.findByTaskId(taskId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(GenerationStatus.COMPLETED);
        assertThat(result.getDurationSeconds()).isEqualTo(30.0);
    }

    @Test
    void 완료콜백_실패_통보면_FAILED이고_원곡만_남는다() throws Exception {
        String taskId = startModification(source, null, 10, 20);

        mockMvc.perform(post("/api/v1/internal/tasks/" + taskId + "/completion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generated_audio_url\":\"FAILED\"}"))
                .andExpect(status().isOk());

        assertThat(statusOf(taskId)).isEqualTo(GenerationStatus.FAILED);
        versions(source.getId()).andExpect(jsonPath("$.result.versions.length()").value(1));
        // 실패하면 같은 곡에 다시 요청할 수 있다
        modify(source.getId(), null, 10, 20).andExpect(status().isOk());
    }

    // ---------- helpers ----------

    private String login(String deviceId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/guest-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"device_id\":\"" + deviceId + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).at("/result/access_token").asText();
    }

    private ResultActions modify(Long generationId, String prompt, Integer start, Integer end) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        if (prompt != null) {
            body.put("prompt", prompt);
        }
        if (start != null) {
            body.put("start_time_sec", start);
        }
        if (end != null) {
            body.put("end_time_sec", end);
        }
        return mockMvc.perform(post("/api/v1/generation/songs/{id}/modifications", generationId)
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.toString()));
    }

    private String startModification(MusicGeneration target, String prompt, int start, int end) throws Exception {
        String body = modify(target.getId(), prompt, start, end)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON202"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).at("/result/task_id").asText();
    }

    private ResultActions complete(String taskId, String audioUrl, double durationSeconds) throws Exception {
        return mockMvc.perform(post("/api/v1/internal/tasks/" + taskId + "/completion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"generated_audio_url\":\"" + audioUrl + "\",\"duration_seconds\":" + durationSeconds + "}"));
    }

    private ResultActions versions(Long rootGenerationId) throws Exception {
        return mockMvc.perform(get("/api/v1/generation/songs/{id}/versions", rootGenerationId)
                .header("Authorization", bearer));
    }

    // parent가 null이면 최초 생성곡(자기 자신이 root)
    private MusicGeneration completed(Member owner, MusicGeneration parent, String resultUrl, Double duration, String prompt) {
        MusicGeneration generation = musicGenerationRepository.save(MusicGeneration.builder()
                .member(owner).melodyScore(melody).parentGeneration(parent)
                .name("곡").genre("pop").atmosphere("happy").prompt(prompt).durationSeconds(duration)
                .build());
        if (parent == null) {
            generation.markAsRoot();
        }
        generation.updateStatus(GenerationStatus.COMPLETED, resultUrl);
        return musicGenerationRepository.save(generation);
    }

    private MusicGeneration pending(Member owner, MusicGeneration parent, GenerationStatus status) {
        MusicGeneration generation = musicGenerationRepository.save(MusicGeneration.builder()
                .member(owner).melodyScore(melody).parentGeneration(parent)
                .name("곡").genre("pop").atmosphere("happy").durationSeconds(30.0)
                .build());
        if (status != GenerationStatus.PROCESSING) {
            generation.updateStatus(status, null);
            generation = musicGenerationRepository.save(generation);
        }
        return generation;
    }

    private List<MusicGeneration> processingIn(MusicGeneration root) {
        return musicGenerationRepository.findByRootGenerationIdAndStatusAndDeletedAtIsNullOrderByIdAsc(
                root.getRootGenerationId(), GenerationStatus.PROCESSING);
    }

    private JsonNode onlyAiRequest() throws Exception {
        assertThat(CapturingWebClientConfig.AI_REQUEST_BODIES).hasSize(1);
        return objectMapper.readTree(CapturingWebClientConfig.AI_REQUEST_BODIES.get(0));
    }

    private GenerationStatus statusOf(String taskId) {
        return musicGenerationRepository.findByTaskId(taskId).orElseThrow().getStatus();
    }
}
