package com.doro.party.common.exception;

import com.doro.party.common.response.ApiResponse;
import com.doro.party.common.web.PageLimits;
import com.hunnit_beasts.doro.sdk.domain.DoroUserContext;
import com.hunnit_beasts.doro.sdk.exception.DoroAccessDeniedException;
import com.hunnit_beasts.doro.sdk.exception.DoroGuardUnavailableException;
import com.hunnit_beasts.doro.sdk.exception.DoroGuardWriteFailedException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** PostgreSQL SQLState: unique_violation */
    private static final String UNIQUE_VIOLATION = "23505";

    @ExceptionHandler(PartyException.class)
    public ResponseEntity<ApiResponse<Void>> handlePartyException(PartyException e) {
        log.warn("PartyException occurred: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return ResponseEntity
                .status(e.getErrorCode().getHttpStatus())
                .body(ApiResponse.error(e.getErrorCode().getHttpStatus(), e.getErrorCode().getCode(), e.getMessage()));
    }

    @ExceptionHandler(DoroAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleDoroAccessDenied(DoroAccessDeniedException e) {
        // 로그인하지 않은 요청은 권한 부족(403)이 아니라 인증 필요(401)다.
        if (!DoroUserContext.getCurrentUser().isAuthenticated()) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage()));
        }
        log.warn("DORO ReBAC Access Denied: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED.getCode(), "인가 검증 실패: 해당 리소스에 대한 권한이 없습니다."));
    }

    /** 인가 서비스(Guard)를 쓸 수 없어 요청을 처리하지 못한 경우: 서버 버그(500)가 아니라 일시적 장애(503)이다. */
    @ExceptionHandler({DoroGuardUnavailableException.class, DoroGuardWriteFailedException.class})
    public ResponseEntity<ApiResponse<Void>> handleGuardUnavailable(RuntimeException e) {
        log.error("DORO Guard unavailable: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.SERVICE_UNAVAILABLE.getCode(), ErrorCode.SERVICE_UNAVAILABLE.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String details = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("Validation error: {}", details);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT.getCode(), details));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        // 내부 메시지(클래스/필드명 등)가 노출되지 않도록 응답에는 일반 문구만 쓰고, 상세는 로그에 남긴다.
        log.warn("IllegalArgumentException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT.getCode(), ErrorCode.INVALID_INPUT.getMessage()));
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(org.springframework.web.HttpRequestMethodNotSupportedException e) {
        log.warn("HttpRequestMethodNotSupportedException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드 요청입니다: " + e.getMethod()));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(org.springframework.http.converter.HttpMessageNotReadableException e) {
        log.warn("HttpMessageNotReadableException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT.getCode(), "요청 본문(Body) 형식이 올바르지 않거나 누락되었습니다."));
    }

    /** 존재하지 않는 경로(스캐너의 /.env, /.git 탐색 등)는 서버 오류가 아니므로 ERROR 로그 없이 404 로 응답한다. */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(org.springframework.web.servlet.resource.NoResourceFoundException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."));
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(org.springframework.web.HttpMediaTypeNotSupportedException e) {
        log.warn("HttpMediaTypeNotSupportedException: {}", e.getContentType());
        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "지원하지 않는 Content-Type 입니다."));
    }

    /** 필수 쿼리 파라미터 누락. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        log.warn("Missing request parameter: {}", e.getParameterName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT.getCode(), "필수 파라미터가 누락되었습니다: " + e.getParameterName()));
    }

    /** 잘못된 UUID/숫자/enum 값 등 파라미터 타입 불일치. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("Request parameter type mismatch: name={}, value={}", e.getName(), e.getValue());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT.getCode(), "파라미터 형식이 올바르지 않습니다: " + e.getName()));
    }

    /** 페이지 번호/크기 범위 등 메서드 파라미터 제약(@Min, @Max) 위반. */
    @ExceptionHandler({HandlerMethodValidationException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiResponse<Void>> handleParameterConstraint(Exception e) {
        log.warn("Request parameter constraint violated: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT.getCode(),
                        "요청 파라미터가 허용 범위를 벗어났습니다. (page ≥ 0, 1 ≤ size ≤ " + PageLimits.MAX_SIZE + ")"));
    }

    /** 업로드 용량 초과는 클라이언트 오류(413)다. */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        log.warn("Upload too large: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "업로드 용량이 허용 한도를 넘었습니다."));
    }

    /** multipart 요청에 필수 파트(file)가 없는 경우. */
    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(org.springframework.web.multipart.support.MissingServletRequestPartException e) {
        log.warn("Missing request part: {}", e.getRequestPartName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT.getCode(), "필수 항목이 누락되었습니다: " + e.getRequestPartName()));
    }

    /** 클라이언트가 받을 수 없는 응답 형식(Accept)을 요구한 경우. */
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotAcceptable(org.springframework.web.HttpMediaTypeNotAcceptableException e) {
        log.warn("HttpMediaTypeNotAcceptableException: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_ACCEPTABLE)
                .body(ApiResponse.error(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", "요청한 응답 형식을 제공할 수 없습니다."));
    }

    /**
     * 유니크 제약 위반(SQLState 23505)만 충돌(409)이다. NOT NULL·길이 초과 같은 다른 무결성 오류는
     * 클라이언트 잘못이 아니라 서버 버그이므로 "이미 존재" 로 가리지 않고 ERROR 로 남기며 500 으로 응답한다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        Throwable cause = e.getMostSpecificCause();
        if (!(cause instanceof java.sql.SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState()))) {
            return handleGenericException(e);
        }
        log.warn("Unique constraint violation: {}", cause.getMessage());
        return ResponseEntity
                .status(ErrorCode.DUPLICATE_RESOURCE.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.DUPLICATE_RESOURCE.getHttpStatus(), ErrorCode.DUPLICATE_RESOURCE.getCode(), ErrorCode.DUPLICATE_RESOURCE.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception e) {
        log.error("Unhandled system exception: ", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR.getCode(), ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
