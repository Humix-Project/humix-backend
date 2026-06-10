package com.humix.api.global.security.jwt;

import com.humix.api.global.security.userdetails.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7);
            String maskedHeader = authorizationHeader.length() > 15 ? 
                    authorizationHeader.substring(0, 15) + "..." + authorizationHeader.substring(authorizationHeader.length() - 4) : 
                    "Bearer ***";
            log.info("Authorization Header: {}", maskedHeader);

            // 토큰 유효성 검증
            if (jwtUtil.isValid(token)) {
                // User 식별자인 UUID 추출
                String uuid = jwtUtil.getUuid(token);

                try {
                    // UUID를 통해 UserDetails 로드
                    UserDetails userDetails = userDetailsService.loadUserByUsername(uuid);

                    if (userDetails != null) {
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (Exception e) {
                    log.error("JwtFilter 인증 에러 상세 정보: ", e);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}