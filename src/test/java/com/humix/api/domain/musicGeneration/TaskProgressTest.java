package com.humix.api.domain.musicGeneration;

import com.humix.api.domain.humming.entity.Humming;
import com.humix.api.domain.humming.repository.HummingRepository;
import com.humix.api.domain.melodyScore.entity.MelodyScore;
import com.humix.api.domain.melodyScore.repository.MelodyScoreRepository;
import com.humix.api.domain.member.entity.Member;
import com.humix.api.domain.member.repository.MemberRepository;
import com.humix.api.domain.musicGeneration.client.RunpodClient;
import com.humix.api.domain.musicGeneration.entity.MusicGeneration;
import com.humix.api.domain.musicGeneration.repository.MusicGenerationRepository;
import com.humix.api.domain.musicGeneration.service.RunpodQueueWatcher;
import com.humix.api.domain.musicGeneration.type.GenerationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 진행 보고 수신, RunPod 상태 확인, 유휴 한도 (#64). 스케줄러는 직접 호출하기 위해 사실상 끈다. */
@SpringBootTest(properties = {
        "generation.stream.idle-timeout-ms=200",
        "runpod.watch.poll-interval-ms=3600000",
        "runpod.metrics.poll-interval-ms=3600000",
        // MockMvc는 SSE 요청을 끝내지 않아 open-in-view가 요청마다 DB 연결을 쥔 채로 남는다. 구독을 여러 번 하므로 끈다.
        "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskProgressTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private MemberRepository memberRepository;
    @Autowired private HummingRepository hummingRepository;
    @Autowired private MelodyScoreRepository melodyScoreRepository;
    @Autowired private MusicGenerationRepository musicGenerationRepository;
    @Autowired private RunpodQueueWatcher watcher;
    @MockitoBean private RunpodClient runpodClient;

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

    // ---------- 진행 보고 수신 ----------

    @Test
    void 진행_보고는_stage와_진행률을_담은_progress_이벤트로_전달된다() throws Exception {
        MusicGeneration generation = processing();
        MvcResult stream = subscribe(generation);

        postProgress(generation.getTaskId(), "{\"status\":\"PROCESSING\",\"stage\":\"GENERATING\",\"progress\":57}")
                .andExpect(status().isOk());

        String body = stream.getResponse().getContentAsString();
        assertThat(body).contains("\"stage\":\"GENERATING\"").contains("\"progress\":57");
    }

    @Test
    void 진행_중이_아닌_작업의_진행_보고는_무시한다() throws Exception {
        MusicGeneration generation = processing();
        MvcResult stream = subscribe(generation);
        generation.updateStatus(GenerationStatus.COMPLETED, "https://example.com/a.wav");
        musicGenerationRepository.save(generation);

        postProgress(generation.getTaskId(), "{\"status\":\"PROCESSING\",\"stage\":\"UPLOADING\",\"progress\":90}")
                .andExpect(status().isOk());

        assertThat(stream.getResponse().getContentAsString()).doesNotContain("UPLOADING");
    }

    @Test
    void 없는_작업의_진행_보고도_200으로_무시한다() throws Exception {
        postProgress("task_unknown", "{\"status\":\"PROCESSING\",\"stage\":\"GENERATING\",\"progress\":10}")
                .andExpect(status().isOk());
    }

    @Test
    void 진행률은_0에서_100으로_보정한다() throws Exception {
        MusicGeneration generation = processing();
        MvcResult stream = subscribe(generation);

        postProgress(generation.getTaskId(), "{\"stage\":\"GENERATING\",\"progress\":250}").andExpect(status().isOk());

        assertThat(stream.getResponse().getContentAsString()).contains("\"progress\":100");
    }

    // ---------- RunPod 상태 확인 ----------

    @Test
    void 대기열에_있으면_IN_QUEUE를_한_번만_알리고_작업은_유지한다() throws Exception {
        MusicGeneration generation = processing();
        MvcResult stream = subscribe(generation);
        runpodReturns("IN_QUEUE");

        watcher.watch();
        watcher.watch();

        String body = stream.getResponse().getContentAsString();
        assertThat(count(body, "\"stage\":\"IN_QUEUE\"")).isEqualTo(1);
        assertThat(statusOf(generation)).isEqualTo(GenerationStatus.PROCESSING);
    }

    @Test
    void RunPod가_실패로_끝낸_작업은_FAILED로_바꾸고_SSE로_알린다() throws Exception {
        for (String runpodStatus : new String[]{"FAILED", "CANCELLED", "TIMED_OUT"}) {
            MusicGeneration generation = processing();
            MvcResult stream = subscribe(generation);
            runpodReturns(runpodStatus);

            watcher.watch();

            assertThat(statusOf(generation)).as(runpodStatus).isEqualTo(GenerationStatus.FAILED);
            assertThat(stream.getResponse().getContentAsString()).as(runpodStatus).contains("\"status\":\"FAILED\"");
        }
    }

    @Test
    void 대기열과_실행_중에는_유휴_한도를_넘겨도_작업을_실패시키지_않는다() throws Exception {
        MusicGeneration generation = processing();
        subscribe(generation);

        for (String runpodStatus : new String[]{"IN_QUEUE", "IN_PROGRESS", "IN_QUEUE"}) {
            Thread.sleep(120); // 한도(200ms)보다 짧은 간격으로 계속 소식이 온다
            runpodReturns(runpodStatus);
            watcher.watch();
        }

        assertThat(statusOf(generation)).isEqualTo(GenerationStatus.PROCESSING);
    }

    @Test
    void 소식이_끊기고_유휴_한도를_넘으면_FAILED로_바꾸고_SSE로_알린다() throws Exception {
        MusicGeneration generation = processing();
        MvcResult stream = subscribe(generation);
        when(runpodClient.getStatus(anyString())).thenReturn(Optional.empty()); // 조회 실패는 소식으로 치지 않는다

        Thread.sleep(300);
        watcher.watch();

        assertThat(statusOf(generation)).isEqualTo(GenerationStatus.FAILED);
        assertThat(stream.getResponse().getContentAsString()).contains("\"status\":\"FAILED\"");
    }

    @Test
    void 완료됐는데_콜백이_없으면_유휴_한도_뒤에_FAILED로_바꾼다() throws Exception {
        MusicGeneration generation = processing();
        subscribe(generation);
        runpodReturns("COMPLETED");

        watcher.watch();
        assertThat(statusOf(generation)).as("콜백을 잠깐 기다린다").isEqualTo(GenerationStatus.PROCESSING);

        Thread.sleep(300);
        watcher.watch();
        assertThat(statusOf(generation)).isEqualTo(GenerationStatus.FAILED);
    }

    @Test
    void 완료_콜백이_오면_RunPod_확인으로_실패_처리되지_않는다() throws Exception {
        MusicGeneration generation = processing();
        subscribe(generation);
        mockMvc.perform(post("/api/v1/internal/tasks/" + generation.getTaskId() + "/completion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generated_audio_url\":\"https://example.com/done.wav\",\"duration_seconds\":30.0}"))
                .andExpect(status().isOk());
        runpodReturns("FAILED");

        watcher.watch();

        assertThat(statusOf(generation)).isEqualTo(GenerationStatus.COMPLETED);
    }

    // ---------- helpers ----------

    private void runpodReturns(String status) {
        when(runpodClient.getStatus(anyString()))
                .thenReturn(Optional.of(new RunpodClient.JobStatus("job", status, null, null)));
    }

    private MvcResult subscribe(MusicGeneration generation) throws Exception {
        return mockMvc.perform(get("/api/v1/generation/songs/tasks/{id}/stream", generation.getTaskId())
                .accept(MediaType.TEXT_EVENT_STREAM)).andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions postProgress(String taskId, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/internal/tasks/" + taskId + "/progress")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private GenerationStatus statusOf(MusicGeneration generation) {
        return musicGenerationRepository.findByTaskId(generation.getTaskId()).orElseThrow().getStatus();
    }

    private MusicGeneration processing() {
        MusicGeneration generation = MusicGeneration.builder()
                .member(member).melodyScore(melody).name("곡").genre("pop").atmosphere("happy")
                .taskId("task_" + UUID.randomUUID()).durationSeconds(30.0).build();
        generation.recordRunpodJob("job-" + UUID.randomUUID());
        generation = musicGenerationRepository.save(generation);
        generation.markAsRoot();
        return musicGenerationRepository.save(generation);
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }
}
