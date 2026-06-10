package com.humix.api.global.security.userdetails;

import com.humix.api.domain.member.entity.Member;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails {

    private final Member member;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 방문자 권한 부여
        return List.of(new SimpleGrantedAuthority("ROLE_MEMBER"));
    }

    @Override
    public String getPassword() {
        return ""; // 투명 로그인이라 비밀번호 없음
    }

    @Override
    public String getUsername() {
        return member.getUuid(); // 식별자로 UUID 반환
    }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}