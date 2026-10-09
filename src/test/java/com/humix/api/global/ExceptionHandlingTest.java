package com.humix.api.global;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExceptionHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String bearer;

    @BeforeEach
    void issueToken() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/guest-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"device_id\":\"exception-test-device\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        bearer = "Bearer " + objectMapper.readTree(body).at("/result/access_token").asText();
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url)
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    void guestLogin_deviceId_누락이면_400과_필드메시지() throws Exception {
        mockMvc.perform(post("/api/v1/auth/guest-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.device_id").exists());
    }

    @Test
    void 곡생성_없는_허밍이면_404() throws Exception {
        postJson("/api/v1/generation/songs",
                "{\"humming_id\":999,\"title\":\"t\",\"genre\":\"pop\",\"mood\":\"happy\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MELODY4000"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void 곡생성_humming_id_누락이면_400() throws Exception {
        postJson("/api/v1/generation/songs", "{\"title\":\"t\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));
    }

    @Test
    void 벡터화_없는_허밍이면_404() throws Exception {
        mockMvc.perform(post("/api/v1/hummings/999/vectors").header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HUMMING4000"));
    }

    @Test
    void 벡터수정_없는_악보면_404() throws Exception {
        mockMvc.perform(put("/api/v1/hummings/999/vectors")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":[]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MELODY4000"));
    }

    @Test
    void 벡터수정_notes_누락이면_400() throws Exception {
        mockMvc.perform(put("/api/v1/hummings/999/vectors")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.notes").exists());
    }

    @Test
    void 곡수정_없는_곡이면_404() throws Exception {
        postJson("/api/v1/generation/songs/999/modifications", "{\"prompt\":\"더 밝게\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GENERATION4000"));
    }

    @Test
    void 곡수정_prompt_누락이면_400() throws Exception {
        postJson("/api/v1/generation/songs/999/modifications", "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.prompt").exists());
    }

    @Test
    void 태스크_취소_없는_태스크면_404() throws Exception {
        mockMvc.perform(delete("/api/v1/generation/songs/tasks/task_unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK4000"));
    }

    @Test
    void 완료콜백_없는_태스크면_404() throws Exception {
        mockMvc.perform(post("/api/v1/internal/tasks/task_unknown/completion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"generated_audio_url\":\"https://example.com/a.wav\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK4000"));
    }

    @Test
    void 완료콜백_generated_audio_url_누락이면_400() throws Exception {
        mockMvc.perform(post("/api/v1/internal/tasks/task_unknown/completion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result.generated_audio_url").exists());
    }

    @Test
    void 깨진_JSON이면_400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/guest-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));
    }

    @Test
    void 경로변수_타입_불일치면_400() throws Exception {
        mockMvc.perform(post("/api/v1/hummings/abc/vectors").header("Authorization", bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));
    }

    @Test
    void 허용되지_않는_메서드면_405() throws Exception {
        mockMvc.perform(put("/api/v1/auth/guest-login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("COMMON405"));
    }

    @Test
    void SSE_구독_없는_태스크면_404_JSON() throws Exception {
        mockMvc.perform(get("/api/v1/generation/songs/tasks/task_unknown/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK4000"));
    }
}
