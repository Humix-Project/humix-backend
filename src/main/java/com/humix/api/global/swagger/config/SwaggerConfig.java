package com.humix.api.global.swagger.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        // 1. JWT 토큰을 위한 SecurityScheme 설정
        String jwtSchemeName = "jwtAuth";
        SecurityScheme securityScheme = new SecurityScheme()
                .name(jwtSchemeName)
                .type(SecurityScheme.Type.HTTP) // HTTP 방식
                .scheme("bearer")               // Bearer 토큰 방식
                .bearerFormat("JWT");           // 포맷은 JWT

        // 2. 전체 API에 보안 요구사항(SecurityRequirement) 적용
        SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);

        return new OpenAPI()
                .addServersItem(new Server().url("https://humix.my-project.cloud").description("운영 서버"))
                .addServersItem(new Server().url("http://localhost:8080").description("로컬 테스트용"))
                .info(new Info()
                        .title("Humix API 명세서 (12시 20분 버전)")
                        .version("1.0.0")
                        .description("허밍 기반의 개인 맞춤형 AI 음악 창작 웹 서비스, Humix의 API 문서입니다."))
                .components(new Components()
                        .addSecuritySchemes(jwtSchemeName, securityScheme)) // Components에 SecurityScheme 추가
                .addSecurityItem(securityRequirement); // 글로벌 시큐리티 적용
    }
}