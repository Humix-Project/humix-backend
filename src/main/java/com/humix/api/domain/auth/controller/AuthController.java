package com.humix.api.domain.auth.controller;

import com.humix.api.domain.member.dto.MemberDTO;
import com.humix.api.domain.member.service.MemberService;
import com.humix.api.global.apiPayload.ApiResponse;
import com.humix.api.global.apiPayload.code.GeneralErrorCode;
import com.humix.api.global.apiPayload.code.GeneralSuccessCode;
import com.humix.api.global.apiPayload.exception.GeneralException;
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

        // 토큰 발급
        TokenDto tokenDto = memberService.registerMember(request);

        // 쿠키 생성 메서드 호출
        ResponseCookie refreshTokenCookie = createRefreshTokenCookie(tokenDto.refreshToken());
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

        // 응답 리턴
        MemberDTO.MemberResponse memberResponse = new MemberDTO.MemberResponse(tokenDto.accessToken());
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, memberResponse);
    }

    @PostMapping("/silent-refresh")
    @Override
    public ApiResponse<MemberDTO.MemberResponse> silentRefresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {

        // 쿠키 자체가 아예 날아오지 않았을 때 방어
        if (refreshToken == null) {
            throw new GeneralException(GeneralErrorCode.REFRESH_TOKEN_EXPIRES);
        }

        // 서비스를 통해 토큰 검증 및 재발급 진행
        TokenDto tokenDto = memberService.refreshTokens(refreshToken);

        // 쿠키 생성 메서드 호출
        ResponseCookie newRefreshTokenCookie = createRefreshTokenCookie(tokenDto.refreshToken());
        response.addHeader(org.springframework.http.HttpHeaders.SET_COOKIE, newRefreshTokenCookie.toString());

        // 응답 리턴
        MemberDTO.MemberResponse memberResponse = new MemberDTO.MemberResponse(tokenDto.accessToken());
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, memberResponse);
    }

    // 쿠키 생성 메서드
    private ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(1209600) // 14일
                .build();
    }
}