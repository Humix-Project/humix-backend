package com.humix.api.global.apiPayload.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GeneralSuccessCode implements BaseSuccessCode{

    OK(HttpStatus.OK,
            "COMMON200",
            "성공입니다."),
    CREATED(HttpStatus.CREATED,
            "COMMON201",
            "생성 성공입니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
