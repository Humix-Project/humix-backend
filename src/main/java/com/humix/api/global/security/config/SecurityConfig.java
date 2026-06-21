package com.humix.api.global.security.config;

import com.humix.api.global.security.handler.CustomAuthenticationEntryPoint;
import com.humix.api.global.security.jwt.JwtFilter;
import com.humix.api.global.security.jwt.JwtUtil;
import com.humix.api.global.security.userdetails.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.http.HttpMethod;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("#{'${cors.allowed-origins}'.split(',')}")
    private List<String> allowedOrigins;

    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CORS 설정 추가 (corsConfigurationSource 메서드 연결)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)

                // 예외 처리 설정 등록
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(customAuthenticationEntryPoint) // 401 에러(로그인 필요) 담당
                )

                // 경로별 권한 설정
                .authorizeHttpRequests(authorize -> authorize
                        // 에러 페이지로 포워딩되는 요청은 무조건 허가
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()

                        // 스웨거 및 health, actuator의 경우 인증 불필요
                        .requestMatchers("/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/v3/api-docs",
                                "/swagger-resources/**",
                                "/webjars/**",
                                "/health",
                                "/actuator/**",
                                "/api/v1/internal/**").permitAll()

                        // 🚨 특정 도메인은 무조건 인증(로그인) 필요하게 막기
                        .requestMatchers("/api/v1/upload/**").authenticated()
                        .requestMatchers("/api/v1/hummings/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/generation/songs", "/api/v1/generation/songs/*/modifications").authenticated()

                        // 나머지는 다 인증 불필요
                        .anyRequest().permitAll()
                )

                // 🛑 현재 프론트와의 연결 테스트를 위해 꺼뒀습니다 🛑
                // JwtFilter를 시큐리티 필터 체인에 끼워 넣기
                .addFilterBefore(new JwtFilter(jwtUtil, userDetailsService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // CORS 전역 설정 세부 내용
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(allowedOrigins);

        // 허용할 HTTP 메서드 (GET, POST 등)
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // 허용할 헤더
        // 🛑프론트와의 연결테스트를 위해 코드를 바꿔두었습니다🛑
        // configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowedOriginPatterns(List.of("*"));

        // 프론트엔드에서 인증 정보(JWT 토큰, 쿠키 등)를 보낼 수 있도록 허용
        configuration.setAllowCredentials(true);

        // 프론트엔드가 응답 헤더에서 Authorization 값을 읽을 수 있도록 허용 (토큰 헤더로 보낼 때 필수)
        configuration.setExposedHeaders(List.of("Authorization"));

        // 모든 API 엔드포인트(/**)에 위 설정 적용
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring()
                .requestMatchers(
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/v3/api-docs",
                        "/swagger-resources/**",
                        "/webjars/**"
                );
    }
}