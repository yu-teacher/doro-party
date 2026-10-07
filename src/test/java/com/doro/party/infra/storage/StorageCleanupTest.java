package com.doro.party.infra.storage;

import com.doro.party.support.PartyHttpTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** DB 트랜잭션과 스토리지의 일관성: 롤백이면 올린 파일을 지우고, 삭제는 커밋된 뒤에만 한다. */
class StorageCleanupTest extends PartyHttpTestBase {

    @Autowired private ObjectStorage storage;
    @Autowired private StorageCleanup cleanup;
    @Autowired private PlatformTransactionManager transactionManager;

    private String put() {
        String key = "test/" + UUID.randomUUID();
        byte[] bytes = {1, 2, 3};
        storage.put(key, new ByteArrayInputStream(bytes), bytes.length, "application/octet-stream");
        return key;
    }

    private boolean stored(String key) {
        try (InputStream ignored = storage.open(key)) {
            return true;
        } catch (StorageException e) {
            return false;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("저장 후 트랜잭션이 롤백되면 올린 파일이 지워지고, 커밋되면 남는다")
    void deleteOnRollback() {
        String rolledBack = put();
        String committed = put();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> {
            cleanup.deleteOnRollback(rolledBack);
            status.setRollbackOnly();
        });
        tx.executeWithoutResult(status -> cleanup.deleteOnRollback(committed));

        assertThat(stored(rolledBack)).isFalse();
        assertThat(stored(committed)).isTrue();
        storage.delete(committed);
    }

    @Test
    @DisplayName("삭제는 커밋된 뒤에만 이루어지고, 롤백되면 파일이 그대로 남는다")
    void deleteAfterCommit() {
        String keptOnRollback = put();
        String removedOnCommit = put();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.executeWithoutResult(status -> {
            cleanup.deleteAfterCommit(List.of(keptOnRollback));
            status.setRollbackOnly();
        });
        tx.executeWithoutResult(status -> {
            cleanup.deleteAfterCommit(List.of(removedOnCommit));
            assertThat(stored(removedOnCommit)).as("커밋 전에는 아직 있다").isTrue();
        });

        assertThat(stored(keptOnRollback)).isTrue();
        assertThat(stored(removedOnCommit)).isFalse();
        storage.delete(keptOnRollback);
    }

    @Test
    @DisplayName("없는 파일을 지우려 해도 오류가 아니다")
    void deletingMissingObjectIsNotAnError() {
        storage.delete("test/does-not-exist-" + UUID.randomUUID());
    }
}
