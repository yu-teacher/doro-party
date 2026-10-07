package com.doro.party.common.exception;

import lombok.Getter;

@Getter
public class PartyException extends RuntimeException {

    private final ErrorCode errorCode;

    public PartyException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public PartyException(ErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }
}
