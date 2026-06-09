package com.humix.api.global.security.userdetails;

import com.humix.api.domain.Member.entity.Member;
import com.humix.api.domain.Member.repository.MemberRepository;
import com.humix.api.global.apiPayload.code.GeneralErrorCode;
import com.humix.api.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String uuid) {
        // 식별자 uuid로 해당하는 유저(Visitor) 찾기
        Member member = memberRepository.findById(uuid)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.MEMBER_NOT_FOUND));

        return new CustomUserDetails(member);
    }
}