package com.humix.api.global.apiPayload.handler;

import com.humix.api.global.apiPayload.ApiResponse;
import com.humix.api.global.apiPayload.code.BaseErrorCode;
import com.humix.api.global.apiPayload.code.GeneralErrorCode;
import com.humix.api.global.apiPayload.exception.GeneralException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GeneralExceptionAdvice {

    /**
     * 1. 비즈니스 로직 에러 (GeneralException)
     * - 서비스에서 직접 발생시킨 커스텀 예외를 처리.
     */
    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(GeneralException ex) {
        BaseErrorCode code = ex.getCode();
        return ResponseEntity
                .status(code.getStatus())
                .body(ApiResponse.onFailure(code, null));
    }

    /**
     * 2. 404 Not Found (NoResourceFoundException)
     * - 잘못된 API 경로로 요청했을 때 발생.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException ex) {
        BaseErrorCode code = GeneralErrorCode.NOT_FOUND;
        return ResponseEntity
                .status(code.getStatus())
                .body(ApiResponse.onFailure(code, null));
    }

    /**
     * 3. 접근 권한 에러 (AccessDeniedException)
     * - @PreAuthorize 등 권한 체크 실패 시 발생
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        BaseErrorCode code = GeneralErrorCode.LOGIN_REQUIRED;
        return ResponseEntity
                .status(code.getStatus())
                .body(ApiResponse.onFailure(code, null));
    }

    /**
     * 4. 그 외 모든 서버 내부 에러 (Exception)
     * - 예상치 못한 NullPointerException 등이 여기서 적발
     * - log.error()를 사용하여 서버 로그에 에러 스택을 남긴다
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleException(Exception ex, HttpServletRequest request) {
        // 에러 로그 남기기 (어떤 요청에서 에러가 났는지 확인용)
        log.error("❌ Unhandled Exception occurred at URL: {}", request.getRequestURI(), ex);

        BaseErrorCode code = GeneralErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity
                .status(code.getStatus())
                .body(ApiResponse.onFailure(code, ex.getMessage()));
    }
}