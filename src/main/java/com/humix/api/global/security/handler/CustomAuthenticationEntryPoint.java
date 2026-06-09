package com.humix.api.global.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.humix.api.global.apiPayload.ApiResponse;
import com.humix.api.global.apiPayload.code.GeneralErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper; // Jackson 라이브러리 (JSON 변환용)

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        // 1. 응답 헤더 설정 (JSON 형태)
        response.setContentType("application/json; charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401 Status Code

        // 2. ApiResponse 생성 (실패 응답)
        ApiResponse<Object> errorResponse = ApiResponse.onFailure(GeneralErrorCode.LOGIN_REQUIRED, null);

        // 3. JSON으로 변환하여 응답 본문에 쓰기
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}