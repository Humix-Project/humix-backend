package com.humix.api.domain.member.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public class MemberDTO {
    @Schema(description = "방문자 등록 요청 (최초 로그인)")
    public record MemberRequest(
            @Schema(description = "기기 고유 식별자 (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
            @NotBlank(message = "device_id는 필수입니다.")
            @JsonProperty("device_id")
            String deviceId
    ) {}

    @Schema(description = "방문자 등록 완료 응답")
    public record MemberResponse(
            @Schema(description = "엑세스 토큰", example = "eyJhbG...")
            @JsonProperty("access_token")
            String accessToken
    ) {}

}
