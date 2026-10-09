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
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON405", "허용되지 않는 요청 방식입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 에러, 잠시 후 다시 시도해주세요."),

    // AUTH 관련 에러
    LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH4000", "로그인이 필요한 기능입니다."),
    REFRESH_TOKEN_EXPIRES(HttpStatus.UNAUTHORIZED, "AUTH4001", "리프레시 토큰이 만료되었습니다. 다시 로그인해주세요."),

    // MEMBER 관련 에러
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER4000", "해당하는 사용자가 존재하지 않습니다."),

    // HUMMING 관련 에러
    HUMMING_NOT_FOUND(HttpStatus.NOT_FOUND, "HUMMING4000", "해당하는 허밍 기록이 존재하지 않습니다."),
    HUMMING_TOO_LONG(HttpStatus.BAD_REQUEST, "HUMMING4001", "허밍은 최대 30초까지 가능합니다."),

    // MELODY 관련 에러
    MELODY_SCORE_NOT_FOUND(HttpStatus.NOT_FOUND, "MELODY4000", "해당 허밍의 멜로디 악보가 존재하지 않습니다."),

    // GENERATION 관련 에러
    GENERATION_NOT_FOUND(HttpStatus.NOT_FOUND, "GENERATION4000", "해당하는 생성곡이 존재하지 않습니다."),

    // TASK 관련 에러
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "TASK4000", "해당 태스크 ID의 작업 정보가 존재하지 않습니다."),

    // UPLOAD 관련 에러
    UNSUPPORTED_AUDIO_TYPE(HttpStatus.BAD_REQUEST, "UPLOAD4000", "지원하지 않는 오디오 형식입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
