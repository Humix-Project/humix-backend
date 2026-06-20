package com.humix.api.domain.member.service;

import com.humix.api.domain.member.dto.MemberDTO;
import com.humix.api.domain.member.entity.Member;
import com.humix.api.domain.member.repository.MemberRepository;
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
}
