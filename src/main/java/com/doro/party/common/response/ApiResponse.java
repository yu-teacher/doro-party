package com.doro.party.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import org.springframework.http.HttpStatus;

import java.time.Instant;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    /** 워크스페이스 표준 오류 규격(code/message/status)의 최상위 필드. 성공 응답에는 없다. */
    private final String code;
    private final String message;
    private final Integer status;
    /** 이전 클라이언트와의 호환을 위해 같은 내용을 중첩 객체로도 내려준다. */
    private final ErrorDetail error;
    private final Instant timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null, null, null, Instant.now());
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(true, null, null, null, null, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(HttpStatus status, String code, String message) {
        return new ApiResponse<>(false, null, code, message, status.value(), new ErrorDetail(code, message), Instant.now());
    }

    @Getter
    @AllArgsConstructor
    public static class ErrorDetail {
        private final String code;
        private final String message;
    }
}
