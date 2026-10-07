package com.doro.party.infra.storage;

/** 오브젝트 스토리지 작업이 실패했다. 원인 예외를 함께 담아 스택트레이스를 보존한다. */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
