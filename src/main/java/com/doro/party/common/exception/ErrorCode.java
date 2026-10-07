package com.doro.party.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // 400 Bad Request
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON-400-01", "잘못된 입력값입니다."),
    LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "LIMIT-400-01", "허용된 개수를 넘었습니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "UPLOAD-400-01", "지원하지 않는 파일 형식이거나 유효하지 않은 이미지입니다."),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD-500-01", "이미지 업로드 처리에 실패했습니다."),

    // 401 Unauthorized
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH-401-01", "인증 자격 증명이 유효하지 않거나 누락되었습니다."),

    // 403 Forbidden
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH-403-01", "요청하신 리소스에 대한 접근 권한이 없습니다."),

    // 404 Not Found
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-404-01", "존재하지 않는 사용자입니다."),
    MAP_NOT_FOUND(HttpStatus.NOT_FOUND, "MAP-404-01", "존재하지 않는 지도입니다."),
    PIN_NOT_FOUND(HttpStatus.NOT_FOUND, "PIN-404-01", "존재하지 않는 핀입니다."),

    // 409 Conflict
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "COMMON-409-01", "이미 존재하는 리소스입니다."),

    // 503 Service Unavailable
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SYS-503-01", "권한 서비스에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."),

    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SYS-500-01", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
