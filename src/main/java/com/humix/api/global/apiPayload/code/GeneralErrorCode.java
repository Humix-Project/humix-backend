package com.humix.api.global.apiPayload.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GeneralErrorCode implements BaseErrorCode{

    // 기본 응답
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON400", "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403", "금지된 요청입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON404", "요청한 리소스를 찾을 수 없습니다. 해당 경로는 존재하지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 에러, 잠시 후 다시 시도해주세요."),

    // AUTH 관련 에러
    LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH4000", "로그인이 필요한 기능입니다."),

    // MEMBER 관련 에러
    MEMBER_NOT_FOUND(HttpStatus.BAD_REQUEST, "MEMBER4000", "해당하는 사용자가 존재하지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
