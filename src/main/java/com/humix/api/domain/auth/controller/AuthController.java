package com.humix.api.domain.auth.controller;

import com.humix.api.domain.member.dto.MemberDTO;
import com.humix.api.domain.member.service.MemberService;
import com.humix.api.global.apiPayload.ApiResponse;
import com.humix.api.global.apiPayload.code.GeneralSuccessCode;
import com.humix.api.global.security.dto.TokenDto;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController implements AuthControllerDocs {

    // yml에서 설정 값 읽어오기
    @Value("${jwt.cookie.secure}")
    private boolean cookieSecure;

    @Value("${jwt.cookie.same-site}")
    private String cookieSameSite;

    private final MemberService memberService;

    @PostMapping("/guest-login")
    @Override
    public ApiResponse<MemberDTO.MemberResponse> guestLogin(
            @RequestBody MemberDTO.MemberRequest request,
            HttpServletResponse response) { // HttpServletResponse 주입받기

        // 1. 서비스를 통해 두 종류의 토큰 받아오기
        TokenDto tokenDto = memberService.registerMember(request);

        // 2. Refresh Token을 위한 HttpOnly 쿠키 생성
        ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", tokenDto.refreshToken())
                .httpOnly(true)
                .secure(cookieSecure)       // HTTP 환경 허용 여부
                .sameSite(cookieSameSite)   // CORS 허용 여부
                .path("/")
                .maxAge(1209600) // 14일
                .build();

        // 3. 응답 헤더에 쿠키 추가
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

        // 4. 응답 바디에는 Access Token만 DTO에 담아서 기존 ApiResponse 서식대로 반환
        MemberDTO.MemberResponse memberResponse = new MemberDTO.MemberResponse(tokenDto.accessToken());

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, memberResponse);
    }

    @PostMapping("/silent-refresh")
    @Override
    public ApiResponse<Object> silentRefresh() {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }
}