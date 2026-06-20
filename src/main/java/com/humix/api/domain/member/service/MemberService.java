package com.humix.api.domain.member.service;

import com.humix.api.domain.member.dto.MemberDTO;
import com.humix.api.domain.member.entity.Member;
import com.humix.api.domain.member.repository.MemberRepository;
import com.humix.api.global.apiPayload.code.GeneralErrorCode;
import com.humix.api.global.apiPayload.exception.GeneralException;
import com.humix.api.global.security.dto.TokenDto;
import com.humix.api.global.security.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {
    private final MemberRepository memberRepository;
    private final JwtUtil jwtUtil;

    // 최초 로그인 (방문자 생성 및 토큰 발급)
    @Transactional
    public TokenDto registerMember(MemberDTO.MemberRequest request) {
        // 1. 엔티티 생성 및 저장 (대문자 MemberRepository -> 소문자 memberRepository로 수정!)
        Member member = Member.builder()
                .uuid(request.deviceId()) // 클라이언트가 보낸 기기 고유 ID 사용 (또는 기존 랜덤 UUID 로직 유지)
                .build();

        Member savedMember = memberRepository.save(member);

        // 2. JWT 토큰 두 종류 모두 발급
        String accessToken = jwtUtil.createAccessToken(savedMember.getUuid());
        String refreshToken = jwtUtil.createRefreshToken(savedMember.getUuid()); // 리프레시 토큰 생성 메서드 호출

        // 3. 두 토큰을 함께 반환
        return new TokenDto(accessToken, refreshToken);
    }

    // 리프레시 토큰 검증 및 재발급
    @Transactional
    public TokenDto refreshTokens(String refreshToken) {
        // 1. 넘어온 리프레시 토큰의 유표성 검증
        if (!jwtUtil.isValid(refreshToken)) {
            throw new GeneralException(GeneralErrorCode.REFRESH_TOKEN_EXPIRES);
        }

        // 2. 토큰에서 UUID 추출
        String uuid = jwtUtil.getUuid(refreshToken);

        // 3. 탈퇴한 유저이거나 없는 유저일 수 있으므로 DB 확인
        Member member = memberRepository.findById(uuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.MEMBER_NOT_FOUND));

        // 4. 엑세스와 리프레시 모두 재발급
        String newAccessToken = jwtUtil.createAccessToken(member.getUuid());
        String newRefreshToken = jwtUtil.createRefreshToken(member.getUuid());

        // 5. 두 토큰을 함께 반환
        return new TokenDto(newAccessToken, newRefreshToken);
    }
}
