package com.humix.api.global;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.humix.api.domain.humming.service.HummingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalErrorResponseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private HummingService hummingService;

    @Test
    void 예상못한_예외는_500이고_내부_메시지를_노출하지_않는다() throws Exception {
        when(hummingService.convertHummingToVector(anyLong()))
                .thenThrow(new IllegalStateException("secret internal detail"));

        String body = mockMvc.perform(post("/api/v1/auth/guest-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"device_id\":\"internal-error-device\"}"))
                .andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + objectMapper.readTree(body).at("/result/access_token").asText();

        mockMvc.perform(post("/api/v1/hummings/1/vectors").header("Authorization", bearer))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON500"))
                .andExpect(jsonPath("$.result").doesNotExist())
                .andExpect(content().string(not(containsString("secret internal detail"))));
    }
}
