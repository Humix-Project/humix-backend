package com.humix.api.global.apiPayload.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AudioCategory {
    HUMMING("사용자가 녹음한 허밍"),
    REFERENCE("사용자가 업로드한 참조곡");

    private final String description;
}