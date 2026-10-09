package com.doro.party.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // 400 Bad Request
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON-400-01", "잘못된 입력값입니다."),
    CANNOT_FRIEND_SELF(HttpStatus.BAD_REQUEST, "FRIEND-400-01", "자기 자신과는 친구가 될 수 없습니다."),
    OWNER_CANNOT_LEAVE(HttpStatus.BAD_REQUEST, "GROUP-400-01", "방장은 모임을 나갈 수 없습니다. 방장을 다른 멤버에게 넘기거나 모임을 삭제하세요."),
    NOT_FRIENDS(HttpStatus.BAD_REQUEST, "FRIEND-400-02", "친구에게만 할 수 있습니다."),
    LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "LIMIT-400-01", "허용된 개수를 넘었습니다."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "UPLOAD-413-01", "사진 크기가 너무 큽니다."),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "UPLOAD-400-01", "지원하지 않는 사진 형식입니다. JPEG, PNG, WebP 사진만 올릴 수 있습니다."),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD-500-01", "사진을 저장하지 못했습니다."),

    // 401 Unauthorized
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH-401-01", "인증 자격 증명이 유효하지 않거나 누락되었습니다."),

    // 403 Forbidden
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH-403-01", "요청하신 리소스에 대한 접근 권한이 없습니다."),

    // 404 Not Found
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-404-01", "존재하지 않는 사용자입니다."),
    FRIEND_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "FRIEND-404-01", "존재하지 않는 친구 요청입니다."),
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "GROUP-404-01", "존재하지 않는 모임입니다."),
    GROUP_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "GROUP-404-02", "모임의 멤버가 아닙니다."),
    SHARE_NOT_FOUND(HttpStatus.NOT_FOUND, "SHARE-404-01", "공유 정보를 찾을 수 없습니다."),
    FRIEND_NOT_FOUND(HttpStatus.NOT_FOUND, "FRIEND-404-02", "친구가 아닙니다."),
    INVITE_NOT_FOUND(HttpStatus.NOT_FOUND, "INVITE-404-01", "유효하지 않거나 만료된 초대 링크입니다."),
    MAP_NOT_FOUND(HttpStatus.NOT_FOUND, "MAP-404-01", "존재하지 않는 지도입니다."),
    PIN_NOT_FOUND(HttpStatus.NOT_FOUND, "PIN-404-01", "존재하지 않는 핀입니다."),
    VISIT_NOT_FOUND(HttpStatus.NOT_FOUND, "VISIT-404-01", "존재하지 않는 방문 기록입니다."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMENT-404-01", "존재하지 않는 댓글입니다."),
    PHOTO_NOT_FOUND(HttpStatus.NOT_FOUND, "PHOTO-404-01", "존재하지 않는 사진입니다."),

    // 409 Conflict
    USERNAME_TAKEN(HttpStatus.CONFLICT, "USER-409-01", "이미 사용 중인 사용자명입니다."),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "COMMON-409-01", "이미 존재하는 리소스입니다."),

    // 503 Service Unavailable
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SYS-503-01", "권한 서비스에 일시적으로 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."),

    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SYS-500-01", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
