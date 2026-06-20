package com.humix.api.global.security.dto;

public record TokenDto(
        String accessToken,
        String refreshToken
) {}