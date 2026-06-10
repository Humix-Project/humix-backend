package com.humix.api.domain.auth.controller;

import com.humix.api.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/auth")
public interface AuthControllerDocs {

    @Operation(summary = "게스트 로그인 API", description = "최초 접속 시 발급한 Device ID로 로그인하여 토큰을 발급받습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "파라미터 에러",
                                    value = "{\"isSuccess\":false, \"code\":\"COMMON400\", \"message\":\"잘못된 요청입니다.\", \"result\":null}")))
    })
    @PostMapping("/guest-login")
    ApiResponse<Object> guestLogin(@RequestBody Object request);

    @Operation(summary = "토큰 재발급(Silent Refresh) API", description = "쿠키에 저장된 Refresh Token을 사용해 Access Token을 재발급합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 만료",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "리프레시 토큰 만료",
                                    value = "{\"isSuccess\":false, \"code\":\"AUTH401\", \"message\":\"리프레시 토큰이 만료되었습니다. 다시 로그인해주세요.\", \"result\":null}")))
    })
    @PostMapping("/silent-refresh")
    ApiResponse<Object> silentRefresh();
}