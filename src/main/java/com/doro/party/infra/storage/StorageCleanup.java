package com.doro.party.infra.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;

/**
 * DB 트랜잭션과 오브젝트 스토리지의 일관성을 맞춘다(GuardTuples 와 같은 원칙).
 * <ul>
 *   <li>저장: 파일을 먼저 올리고 DB 에 기록한다. DB 가 롤백되면 올린 파일을 지워 고아 파일이 남지 않게 한다.</li>
 *   <li>삭제: DB 삭제가 커밋된 뒤에 파일을 지운다. 먼저 지웠는데 DB 삭제가 실패하면 기록만 남고 파일이 사라진다.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageCleanup {

    private final ObjectStorage storage;

    /** 현재 트랜잭션이 롤백되면 방금 올린 파일을 지운다. */
    public void deleteOnRollback(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deleteQuietly(key, "rolled back: removing orphan object");
                }
            }
        });
    }

    /** 현재 트랜잭션이 커밋된 뒤에 파일들을 지운다. 트랜잭션이 없으면 바로 지운다. */
    public void deleteAfterCommit(Collection<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            keys.forEach(key -> deleteQuietly(key, "deleting object"));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                keys.forEach(key -> deleteQuietly(key, "committed: deleting object"));
            }
        });
    }

    private void deleteQuietly(String key, String what) {
        try {
            storage.delete(key);
            log.info("Storage cleanup ({}): key={}", what, key);
        } catch (StorageException e) {
            // 사용자 요청은 이미 처리됐으므로 실패시키지 않고, 남은 파일을 추적할 수 있게 ERROR 로 남긴다.
            log.error("Storage cleanup failed ({}): key={} - orphan object remains", what, key, e);
        }
    }
}
