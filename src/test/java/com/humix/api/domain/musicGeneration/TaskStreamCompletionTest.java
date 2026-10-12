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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SSE `complete` 이벤트의 result 내용 (#46: root_generation_id). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskStreamCompletionTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MemberRepository memberRepository;
    @Autowired private HummingRepository hummingRepository;
    @Autowired private MelodyScoreRepository melodyScoreRepository;
    @Autowired private MusicGenerationRepository musicGenerationRepository;

    private Member member;
    private MelodyScore melody;

    @BeforeEach
    void setUp() {
        member = memberRepository.save(Member.builder().uuid(UUID.randomUUID().toString()).build());
        Humming humming = hummingRepository.save(Humming.builder()
                .member(member).s3FileUrl("https://example.com/humming.wav").durationSeconds(10).build());
        melody = melodyScoreRepository.save(MelodyScore.builder()
                .humming(humming)
                .notesData("[{\"start_time_seconds\":0.0,\"pitch\":60,\"duration_seconds\":1.0}]")
                .build());
    }

    // ---------- 이미 완료된 작업을 구독 ----------

    @Test
    void 이미_완료된_최초_생성곡은_root_generation_id가_song_id와_같다() throws Exception {
        MusicGeneration root = completedRoot();

        JsonNode result = subscribeAndReadComplete(root.getTaskId());

        assertThat(result.path("song_id").asLong()).isEqualTo(root.getId());
        assertThat(result.path("root_generation_id").asLong()).isEqualTo(root.getId());
        assertThat(result.path("task_id").asText()).isEqualTo(root.getTaskId());
        assertThat(result.path("audio_url").asText()).isEqualTo(root.getResultS3Url());
        assertThat(result.path("duration_seconds").asDouble()).isEqualTo(30.0);
    }

    @Test
    void 이미_완료된_재생성본은_원본의_root_generation_id를_이어받는다() throws Exception {
        MusicGeneration root = completedRoot();
        MusicGeneration child = completedChild(root);

        JsonNode result = subscribeAndReadComplete(child.getTaskId());

        assertThat(result.path("song_id").asLong()).isEqualTo(child.getId());
        assertThat(result.path("root_generation_id").asLong()).isEqualTo(root.getId());
        assertThat(result.path("song_id").asLong()).isNotEqualTo(result.path("root_generation_id").asLong());
    }

    // ---------- 구독 중 AI 완료 콜백 수신 ----------

    @Test
    void 구독_중_콜백으로_완료된_최초_생성곡도_root_generation_id가_song_id와_같다() throws Exception {
        MusicGeneration root = processingRoot();

        JsonNode result = subscribeThenCallback(root.getTaskId());

        assertThat(result.path("song_id").asLong()).isEqualTo(root.getId());
        assertThat(result.path("root_generation_id").asLong()).isEqualTo(root.getId());
    }

    @Test
    void 구독_중_콜백으로_완료된_재생성본은_원본의_root_generation_id를_담는다() throws Exception {
        MusicGeneration root = completedRoot();
        MusicGeneration child = processingChild(root);

        JsonNode result = subscribeThenCallback(child.getTaskId());

        assertThat(result.path("song_id").asLong()).isEqualTo(child.getId());
        assertThat(result.path("root_generation_id").asLong()).isEqualTo(root.getId());
        assertThat(result.path("audio_url").asText()).isEqualTo("https://example.com/callback.wav");
    }

    // ---------- helpers ----------

    private JsonNode subscribeAndReadComplete(String taskId) throws Exception {
        MvcResult stream = mockMvc.perform(get("/api/v1/generation/songs/tasks/{id}/stream", taskId)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andReturn();
        return completeResult(stream.getResponse().getContentAsString());
    }

    // 스트림을 먼저 구독(PROCESSING)해 둔 뒤 AI 완료 콜백을 보내고, 같은 응답에 쌓인 complete 이벤트를 읽는다
    private JsonNode subscribeThenCallback(String taskId) throws Exception {
        MvcResult stream = mockMvc.perform(get("/api/v1/generation/songs/tasks/{id}/stream", taskId)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andReturn();
        assertThat(stream.getResponse().getContentAsString()).doesNotContain("event:complete");

        mockMvc.perform(post("/api/v1/internal/tasks/" + taskId + "/completion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generated_audio_url\":\"https://example.com/callback.wav\",\"duration_seconds\":30.0}"))
                .andExpect(status().isOk());

        return completeResult(stream.getResponse().getContentAsString());
    }

    // SSE 본문에서 `event:complete` 다음 `data:` 줄의 JSON을 읽어 result를 반환한다
    private JsonNode completeResult(String sseBody) throws Exception {
        int at = sseBody.indexOf("event:complete");
        assertThat(at).as("complete 이벤트가 있어야 한다: %s", sseBody).isGreaterThanOrEqualTo(0);
        String data = sseBody.substring(at).lines()
                .filter(line -> line.startsWith("data:"))
                .findFirst().orElseThrow()
                .substring("data:".length());
        JsonNode event = objectMapper.readTree(data);
        assertThat(event.path("status").asText()).isEqualTo("COMPLETED");
        return event.path("result");
    }

    private MusicGeneration completedRoot() {
        MusicGeneration root = processingRoot();
        root.updateStatus(GenerationStatus.COMPLETED, "https://example.com/" + UUID.randomUUID() + ".wav");
        return musicGenerationRepository.save(root);
    }

    private MusicGeneration processingRoot() {
        MusicGeneration root = musicGenerationRepository.save(MusicGeneration.builder()
                .member(member).melodyScore(melody).name("곡").genre("pop").atmosphere("happy")
                .taskId("task_" + UUID.randomUUID()).durationSeconds(30.0).build());
        root.markAsRoot();
        return musicGenerationRepository.save(root);
    }

    private MusicGeneration processingChild(MusicGeneration parent) {
        return musicGenerationRepository.save(MusicGeneration.builder()
                .member(member).melodyScore(melody).parentGeneration(parent)
                .name("곡").genre("pop").atmosphere("happy")
                .taskId("task_" + UUID.randomUUID()).durationSeconds(30.0).build());
    }

    private MusicGeneration completedChild(MusicGeneration parent) {
        MusicGeneration child = processingChild(parent);
        child.updateStatus(GenerationStatus.COMPLETED, "https://example.com/" + UUID.randomUUID() + ".wav");
        return musicGenerationRepository.save(child);
    }
}
