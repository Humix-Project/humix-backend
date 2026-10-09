package com.humix.api.domain.auth.controller;

import com.humix.api.domain.member.dto.MemberDTO;
import com.humix.api.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/auth")
public interface AuthControllerDocs {

    @Operation(summary = "게스트 로그인 API", description = "최초 접속 시 발급한 Device ID로 로그인하여 토큰을 발급받습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 값 검증 실패",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "검증 실패",
                                    value = "{\"isSuccess\":false, \"code\":\"COMMON400\", \"message\":\"잘못된 요청입니다.\", \"result\":{\"device_id\":\"device_id는 필수입니다.\"}}")))
    })
    @PostMapping("/guest-login")
    ApiResponse<MemberDTO.MemberResponse> guestLogin(@RequestBody MemberDTO.MemberRequest request,
                                                     HttpServletResponse response);

    @Operation(summary = "토큰 재발급(Silent Refresh) API", description = "쿠키에 저장된 Refresh Token을 사용해 Access Token을 재발급합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 만료",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "리프레시 토큰 만료",
                                    value = "{\"isSuccess\":false, \"code\":\"AUTH4001\", \"message\":\"리프레시 토큰이 만료되었습니다. 다시 로그인해주세요.\", \"result\":null}"))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 없음",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "사용자 없음",
                                    value = "{\"isSuccess\":false, \"code\":\"MEMBER4000\", \"message\":\"해당하는 사용자가 존재하지 않습니다.\", \"result\":null}")))
    })
    @PostMapping("/silent-refresh")
    ApiResponse<MemberDTO.MemberResponse> silentRefresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response);
}