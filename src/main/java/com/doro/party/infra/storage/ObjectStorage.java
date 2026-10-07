package com.doro.party.infra.storage;

import java.io.InputStream;

/** 사진 같은 바이너리를 두는 비공개 오브젝트 스토리지. 읽기는 항상 권한을 확인한 백엔드를 거친다. */
public interface ObjectStorage {

    /** 오브젝트를 저장한다. 실패하면 {@link StorageException}. */
    void put(String key, InputStream data, long size, String contentType);

    /** 오브젝트를 연다. 호출자가 스트림을 닫아야 한다. 없으면 {@link StorageException}. */
    InputStream open(String key);

    /** 오브젝트를 지운다. 없어도 오류가 아니다. */
    void delete(String key);
}
