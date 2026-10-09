package com.humix.api.domain.musicGeneration;

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
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GenerationVersionTest {

    // 생성/수정 API가 RunPod로 보내는 요청을 받아주는 가짜 서버 (실제 네트워크 호출 방지)
    private static final HttpServer FAKE_RUNPOD = startFakeRunpod();

    private static HttpServer startFakeRunpod() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void runpodProperties(DynamicPropertyRegistry registry) {
        registry.add("runpod.endpoint-url", () -> "http://localhost:" + FAKE_RUNPOD.getAddress().getPort());
    }

    @AfterAll
    static void stopFakeRunpod() {
        FAKE_RUNPOD.stop(0);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MemberRepository memberRepository;
    @Autowired private HummingRepository hummingRepository;
    @Autowired private MelodyScoreRepository melodyScoreRepository;
    @Autowired private MusicGenerationRepository musicGenerationRepository;

    private String bearer;
    private Member member;
    private Humming humming;
    private MelodyScore melody;

    @BeforeEach
    void setUp() throws Exception {
        // 테스트끼리 데이터가 섞이지 않도록 매번 새 사용자로 시작한다 (member.uuid는 36자)
        String deviceId = UUID.randomUUID().toString();
        bearer = login(deviceId);
        member = memberRepository.findById(deviceId).orElseThrow();
        humming = hummingRepository.save(Humming.builder()
                .member(member).s3FileUrl("https://example.com/humming.wav").durationSeconds(10).build());
        melody = melodyScoreRepository.save(MelodyScore.builder()
                .humming(humming)
                .notesData("[{\"start_time_seconds\":0.0,\"pitch\":60,\"duration_seconds\":1.0}]")
                .build());
    }

    // ---------- 되돌리기 ----------

    @Test
    void 되돌리기는_기준_버전_이후만_삭제하고_목록에는_남은_버전만_보인다() throws Exception {
        MusicGeneration v1 = version(member, null, GenerationStatus.COMPLETED);
        MusicGeneration v2 = version(member, v1, GenerationStatus.COMPLETED);
        MusicGeneration v3 = version(member, v2, GenerationStatus.COMPLETED);
        MusicGeneration v4 = version(member, v3, GenerationStatus.COMPLETED);

        revert(bearer, v2.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.generation_id").value(v2.getId()))
                .andExpect(jsonPath("$.result.deleted_count").value(2));

        assertThat(deletedAt(v1)).isNull();
        assertThat(deletedAt(v2)).isNull();
        assertThat(deletedAt(v3)).isNotNull();
        assertThat(deletedAt(v4)).isNotNull();

        versions(bearer, v1.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.root_generation_id").value(v1.getId()))
                .andExpect(jsonPath("$.result.versions.length()").value(2))
                .andExpect(jsonPath("$.result.versions[0].generation_id").value(v1.getId()))
                .andExpect(jsonPath("$.result.versions[0].version_no").value(1))
                .andExpect(jsonPath("$.result.versions[1].generation_id").value(v2.getId()))
                .andExpect(jsonPath("$.result.versions[1].version_no").value(2));
    }

    @Test
    void 되돌린_뒤_새_버전이_생기면_번호가_이어진다() throws Exception {
        MusicGeneration v1 = version(member, null, GenerationStatus.COMPLETED);
        MusicGeneration v2 = version(member, v1, GenerationStatus.COMPLETED);
        MusicGeneration v3 = version(member, v2, GenerationStatus.COMPLETED);

        revert(bearer, v1.getId()).andExpect(status().isOk());
        // 삭제된 v2, v3 대신 v1에서 새로 수정본을 만든 상황
        MusicGeneration v4 = version(member, v1, GenerationStatus.COMPLETED);

        versions(bearer, v1.getId())
                .andExpect(jsonPath("$.result.versions.length()").value(2))
                .andExpect(jsonPath("$.result.versions[1].generation_id").value(v4.getId()))
                .andExpect(jsonPath("$.result.versions[1].version_no").value(2));
        assertThat(deletedAt(v2)).isNotNull();
        assertThat(deletedAt(v3)).isNotNull();
    }

    @Test
    void 마지막_버전으로_되돌리면_삭제_0건() throws Exception {
        MusicGeneration v1 = version(member, null, GenerationStatus.COMPLETED);
        MusicGeneration v2 = version(member, v1, GenerationStatus.COMPLETED);

        revert(bearer, v2.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.deleted_count").value(0));
        assertThat(deletedAt(v1)).isNull();
        assertThat(deletedAt(v2)).isNull();
    }

    @Test
    void 같은_root에_진행중인_작업이_있으면_409이고_아무것도_삭제하지_않는다() throws Exception {
        MusicGeneration v1 = version(member, null, GenerationStatus.COMPLETED);
        MusicGeneration v2 = version(member, v1, GenerationStatus.COMPLETED);
        MusicGeneration v3 = version(member, v2, GenerationStatus.PROCESSING);

        revert(bearer, v1.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GENERATION4001"));

        assertThat(deletedAt(v2)).isNull();
        assertThat(deletedAt(v3)).isNull();
    }

    @Test
    void 다른_곡의_진행중인_작업은_되돌리기를_막지_않는다() throws Exception {
        MusicGeneration a1 = version(member, null, GenerationStatus.COMPLETED);
        MusicGeneration a2 = version(member, a1, GenerationStatus.COMPLETED);
        version(member, null, GenerationStatus.PROCESSING); // 다른 root

        revert(bearer, a1.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.deleted_count").value(1));
        assertThat(deletedAt(a2)).isNotNull();
    }

    @Test
    void 이미_삭제된_버전이나_미완료_버전으로는_되돌릴_수_없다() throws Exception {
        MusicGeneration v1 = version(member, null, GenerationStatus.COMPLETED);
        MusicGeneration v2 = version(member, v1, GenerationStatus.COMPLETED);
        MusicGeneration failed = version(member, v1, GenerationStatus.FAILED);

        revert(bearer, failed.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GENERATION4000"));

        revert(bearer, v1.getId()).andExpect(status().isOk());
        revert(bearer, v2.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GENERATION4000"));
    }

    @Test
    void 없는_곡이나_타인의_곡은_404() throws Exception {
        Member other = memberRepository.save(Member.builder().uuid(UUID.randomUUID().toString()).build());
        MusicGeneration othersSong = version(other, null, GenerationStatus.COMPLETED);
        MusicGeneration othersLater = version(other, othersSong, GenerationStatus.COMPLETED);

        revert(bearer, 999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GENERATION4000"));
        revert(bearer, othersSong.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GENERATION4000"));
        versions(bearer, othersSong.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GENERATION4000"));

        assertThat(deletedAt(othersLater)).isNull();
    }

    @Test
    void 로그인_없이는_401() throws Exception {
        mockMvc.perform(post("/api/v1/generation/songs/1/revert"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/generation/songs/1/versions"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 버전 목록 ----------

    @Test
    void 버전_목록은_완료된_버전만_생성_순서로_보여주고_번호는_연속이다() throws Exception {
        MusicGeneration v1 = version(member, null, GenerationStatus.COMPLETED);
        version(member, v1, GenerationStatus.FAILED);
        version(member, v1, GenerationStatus.CANCELED);
        version(member, v1, GenerationStatus.PROCESSING);
        MusicGeneration v5 = version(member, v1, GenerationStatus.COMPLETED);

        versions(bearer, v1.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.versions.length()").value(2))
                .andExpect(jsonPath("$.result.versions[0].generation_id").value(v1.getId()))
                .andExpect(jsonPath("$.result.versions[0].version_no").value(1))
                .andExpect(jsonPath("$.result.versions[0].audio_url").exists())
                .andExpect(jsonPath("$.result.versions[1].generation_id").value(v5.getId()))
                .andExpect(jsonPath("$.result.versions[1].version_no").value(2));
    }

    @Test
    void 버전_목록은_원본_ID만_받고_수정본_ID나_없는_ID는_404() throws Exception {
        MusicGeneration v1 = version(member, null, GenerationStatus.COMPLETED);
        MusicGeneration v2 = version(member, v1, GenerationStatus.COMPLETED);

        versions(bearer, v1.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.root_generation_id").value(v1.getId()));
        versions(bearer, v2.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GENERATION4000"));
        versions(bearer, 999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GENERATION4000"));
    }

    // ---------- 기존 조회/수정 API 반영 ----------

    @Test
    void 내_곡_목록에서_삭제된_버전은_빠진다() throws Exception {
        MusicGeneration v1 = version(member, null, GenerationStatus.COMPLETED);
        MusicGeneration v2 = version(member, v1, GenerationStatus.COMPLETED);
        version(member, v2, GenerationStatus.COMPLETED);

        mockMvc.perform(get("/api/v1/me/songs").header("Authorization", bearer))
                .andExpect(jsonPath("$.result.total_count").value(3));

        revert(bearer, v1.getId()).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me/songs").header("Authorization", bearer))
                .andExpect(jsonPath("$.result.total_count").value(1))
                .andExpect(jsonPath("$.result.contents[0].song_id").value(v1.getId()));
    }

    @Test
    void 삭제된_버전은_수정_요청_대상이_될_수_없다() throws Exception {
        MusicGeneration v1 = version(member, null, GenerationStatus.COMPLETED);
        MusicGeneration v2 = version(member, v1, GenerationStatus.COMPLETED);
        revert(bearer, v1.getId()).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/generation/songs/{id}/modifications", v2.getId())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"더 밝게\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GENERATION4000"));
    }

    // ---------- root 설정 ----------

    @Test
    void 생성은_자기_자신을_root로_수정은_부모의_root를_물려받는다() throws Exception {
        String createBody = mockMvc.perform(post("/api/v1/generation/songs")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"humming_id\":" + humming.getId() + ",\"title\":\"t\",\"genre\":\"pop\",\"mood\":\"happy\"}"))
                .andExpect(jsonPath("$.code").value("COMMON202"))
                .andReturn().getResponse().getContentAsString();
        MusicGeneration root = musicGenerationRepository
                .findByTaskId(objectMapper.readTree(createBody).at("/result/task_id").asText()).orElseThrow();
        assertThat(root.getRootGenerationId()).isEqualTo(root.getId());

        root.updateStatus(GenerationStatus.COMPLETED, "https://example.com/root.wav");
        musicGenerationRepository.save(root);

        String modifyBody = mockMvc.perform(post("/api/v1/generation/songs/{id}/modifications", root.getId())
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"더 밝게\"}"))
                .andExpect(jsonPath("$.code").value("COMMON202"))
                .andReturn().getResponse().getContentAsString();
        MusicGeneration child = musicGenerationRepository
                .findByTaskId(objectMapper.readTree(modifyBody).at("/result/task_id").asText()).orElseThrow();
        assertThat(child.getRootGenerationId()).isEqualTo(root.getId());
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

    private ResultActions revert(String bearer, Long generationId) throws Exception {
        return mockMvc.perform(post("/api/v1/generation/songs/{id}/revert", generationId)
                .header("Authorization", bearer));
    }

    private ResultActions versions(String bearer, Long generationId) throws Exception {
        return mockMvc.perform(get("/api/v1/generation/songs/{id}/versions", generationId)
                .header("Authorization", bearer));
    }

    private LocalDateTime deletedAt(MusicGeneration generation) {
        return musicGenerationRepository.findById(generation.getId()).orElseThrow().getDeletedAt();
    }

    // parent가 null이면 최초 생성곡(자기 자신이 root)
    private MusicGeneration version(Member owner, MusicGeneration parent, GenerationStatus status) {
        MusicGeneration generation = musicGenerationRepository.save(MusicGeneration.builder()
                .member(owner).melodyScore(melody).parentGeneration(parent)
                .name("곡").genre("pop").atmosphere("happy")
                .build());
        if (parent == null) {
            generation.markAsRoot();
        }
        if (status != GenerationStatus.PROCESSING) {
            generation.updateStatus(status,
                    status == GenerationStatus.COMPLETED ? "https://example.com/" + generation.getId() + ".wav" : null);
        }
        return musicGenerationRepository.save(generation);
    }
}
