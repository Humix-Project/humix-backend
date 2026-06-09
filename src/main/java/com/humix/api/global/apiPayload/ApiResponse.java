package com.humix.api.global.apiPayload;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.humix.api.global.apiPayload.code.BaseErrorCode;
import com.humix.api.global.apiPayload.code.BaseSuccessCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@AllArgsConstructor
@JsonPropertyOrder({"isSuccess", "code", "message", "result"})
public class ApiResponse<T> {

    @Schema(description = "성공 여부", example = "true")
    @JsonProperty("isSuccess")
    private final Boolean isSuccess;

    @Schema(description = "응답 코드", example = "COMMON200")
    @JsonProperty("code")
    private final String code;

    @Schema(description = "응답 메시지", example = "성공입니다.")
    @JsonProperty("message")
    private final String message;

    @Schema(description = "응답 데이터")
    @JsonProperty("result")
    private T result;

    // 성공 응답
    public static <T> ApiResponse<T> onSuccess(BaseSuccessCode code, T result) {
        return new ApiResponse<>(true, code.getCode(), code.getMessage(), result);
    }

    // 실패 응답
    public static <T> ApiResponse<T> onFailure(BaseErrorCode code, T result) {
        return new ApiResponse<>(false, code.getCode(), code.getMessage(), result);
    }}
