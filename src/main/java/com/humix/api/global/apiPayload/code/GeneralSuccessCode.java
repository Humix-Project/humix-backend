package com.humix.api.global.apiPayload.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GeneralSuccessCode implements BaseSuccessCode{

    OK(HttpStatus.OK, "COMMON200", "성공입니다."),
    CREATED(HttpStatus.CREATED, "COMMON201", "생성 성공입니다."),

    // 비동기 작업 요청 수락용
    ACCEPTED(HttpStatus.ACCEPTED, "COMMON202", "요청이 수락되어 처리가 시작되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
