package com.humix.api.global.apiPayload.handler;

import com.humix.api.global.apiPayload.ApiResponse;
import com.humix.api.global.apiPayload.code.BaseErrorCode;
import com.humix.api.global.apiPayload.code.GeneralErrorCode;
import com.humix.api.global.apiPayload.exception.GeneralException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GeneralExceptionAdvice {

    // 요청 JSON 필드명(snake_case)과 맞추기 위해 자바 필드명을 변환한다.
    private static String toSnakeCase(String field) {
        return field.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    /**
     * 에러 응답은 항상 JSON으로 내려준다.
     * - SSE 엔드포인트(produces = text/event-stream)에서 난 예외도 Accept 협상 없이 JSON 본문을 쓸 수 있도록 Content-Type을 명시한다.
     */
    private static <T> ResponseEntity<ApiResponse<T>> failure(BaseErrorCode code, T result) {
        return ResponseEntity
                .status(code.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.onFailure(code, result));
    }

    /**
     * 1. 비즈니스 로직 에러 (GeneralException)
     * - 서비스에서 직접 발생시킨 커스텀 예외를 처리.
     */
    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(GeneralException ex) {
        BaseErrorCode code = ex.getCode();
        return failure(code, null);
    }

    /**
     * 2. 404 Not Found (NoResourceFoundException)
     * - 잘못된 API 경로로 요청했을 때 발생.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException ex) {
        BaseErrorCode code = GeneralErrorCode.NOT_FOUND;
        return failure(code, null);
    }

    /**
     * 3. 접근 권한 에러 (AccessDeniedException)
     * - @PreAuthorize 등 권한 체크 실패 시 발생
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        BaseErrorCode code = GeneralErrorCode.LOGIN_REQUIRED;
        return failure(code, null);
    }

    /**
     * 4. 요청 값 검증 실패 (@Valid)
     * - result에 {필드명: 검증 메시지}를 담아 반환한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(toSnakeCase(error.getField()), error.getDefaultMessage()));

        BaseErrorCode code = GeneralErrorCode.BAD_REQUEST;
        return failure(code, errors);
    }

    /**
     * 5. 읽을 수 없는 요청 (깨진 JSON, 타입 불일치, 필수 파라미터 누락)
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequestException(Exception ex) {
        BaseErrorCode code = GeneralErrorCode.BAD_REQUEST;
        return failure(code, null);
    }

    /**
     * 6. 허용되지 않는 HTTP 메서드 (405)
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        BaseErrorCode code = GeneralErrorCode.METHOD_NOT_ALLOWED;
        return failure(code, null);
    }

    /**
     * 7. 그 외 모든 서버 내부 에러 (Exception)
     * - 예상치 못한 NullPointerException 등이 여기서 적발
     * - log.error()를 사용하여 서버 로그에 에러 스택을 남기고, 응답에는 내부 메시지를 노출하지 않는다
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex, HttpServletRequest request) {
        // 에러 로그 남기기 (어떤 요청에서 에러가 났는지 확인용)
        log.error("❌ Unhandled Exception occurred at URL: {}", request.getRequestURI(), ex);

        BaseErrorCode code = GeneralErrorCode.INTERNAL_SERVER_ERROR;
        return failure(code, null);
    }
}