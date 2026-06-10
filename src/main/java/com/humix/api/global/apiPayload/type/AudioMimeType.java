package com.humix.api.global.apiPayload.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AudioMimeType {
    MP3("audio/mpeg", ".mp3 유형 오디오"),
    WAV("audio/wav", ".wav 유형 오디오");

    // 명세서에 있는 실제 문자열 값을 매핑
    private final String mimeType;
    private final String description;

    // (선택) 클라이언트가 보낸 문자열로 Enum을 찾는 편의 메서드
    public static AudioMimeType from(String mimeType) {
        for (AudioMimeType type : AudioMimeType.values()) {
            if (type.getMimeType().equalsIgnoreCase(mimeType)) {
                return type;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 오디오 형식입니다: " + mimeType);
    }
}