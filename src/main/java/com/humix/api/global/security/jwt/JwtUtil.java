package com.humix.api.global.security.jwt;

import com.humix.api.global.security.userdetails.CustomUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.stream.Collectors;

@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final Duration accessExpiration;
    private final Duration refreshExpiration;

    public JwtUtil(
            @Value("${jwt.token.secret-key}") String secret, // yml 설정 키 이름 확인 필요
            @Value("${jwt.token.expiration.access}") Long accessExpiration,
            @Value("${jwt.token.expiration.refresh}") Long refreshExpiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpiration = Duration.ofMillis(accessExpiration);
        this.refreshExpiration = Duration.ofMillis(refreshExpiration);
    }

    // 1. [로그인용] CustomUserDetails 객체로 토큰 생성
    public String createAccessToken(CustomUserDetails user) {
        return createToken(user.getUsername(), user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(",")),
                accessExpiration);
    }

    // 2. [신규 발급용] UUID로 직접 생성
    public String createAccessToken(String uuid) {
        return createToken(uuid, "ROLE_VISITOR", accessExpiration);
    }
    // 리프레시 토큰은 대개 권한(Role) 체크에 직접 쓰이지 않으므로 빈 값이나 특정 식별용 권한을 넘깁니다.
    public String createRefreshToken(String uuid) {
        return createToken(uuid, "ROLE_REFRESH", refreshExpiration); // 리프레시 전용 만료 시간 전달
    }

    // 3. 토큰에서 UUID(Subject) 추출
    public String getUuid(String token) {
        try {
            return getClaims(token).getPayload().getSubject();
        } catch (JwtException e) {
            return null;
        }
    }

    // 4. 유효성 검증
    public boolean isValid(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    // 내부 토큰 생성 메서드
    // 파라미터: UUID, 권한
    private String createToken(String uuid, String authorities, Duration expiration) {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject(uuid) // Subject에 UUID 저장
                .claim("role", authorities)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration))) // 이제 파라미터로 받은 expiration을 정상적으로 사용!
                .signWith(secretKey)
                .compact();
    }

    private Jws<Claims> getClaims(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(secretKey)
                .clockSkewSeconds(60)
                .build()
                .parseSignedClaims(token);
    }
}