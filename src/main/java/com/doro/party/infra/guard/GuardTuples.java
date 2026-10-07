package com.doro.party.infra.guard;

import com.hunnit_beasts.doro.sdk.client.DoroGuardClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * DB 트랜잭션과 Guard(원격) 관계 튜플의 일관성을 맞추는 래퍼.
 *
 * <p>Guard 는 같은 트랜잭션에 참여할 수 없으므로 방향별로 안전한 쪽을 택한다.
 * <ul>
 *   <li><b>쓰기</b>: 트랜잭션 안에서 즉시 쓰되 실패하면 예외를 던져 DB 도 롤백한다. 튜플 없이 글만 생기면
 *       작성자가 자기 글을 수정하지 못하기 때문이다. 이후 DB 가 롤백되면 이미 쓴 튜플을 정리한다.</li>
 *   <li><b>삭제</b>: 커밋이 확정된 뒤에 지운다. 먼저 지웠는데 DB 삭제가 실패하면 글은 남고 권한만 사라진다.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuardTuples {

    private final DoroGuardClient guardClient;

    /** 관계 튜플을 쓴다. Guard 쓰기가 실패하면 예외가 전파되어 호출한 트랜잭션이 롤백된다. */
    public void write(String namespace, String objectId, String relation, String subjectNamespace, String subjectId) {
        guardClient.writeTupleOrThrow(namespace, objectId, relation, subjectNamespace, subjectId, null);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        cleanUpOrphan(namespace, objectId, relation, subjectNamespace, subjectId);
                    }
                }
            });
        }
    }

    /** 현재 트랜잭션이 커밋된 뒤에 튜플을 지운다. 트랜잭션이 없으면 바로 지운다. */
    public void deleteAfterCommit(String namespace, String objectId, String relation, String subjectNamespace, String subjectId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(namespace, objectId, relation, subjectNamespace, subjectId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(namespace, objectId, relation, subjectNamespace, subjectId);
            }
        });
    }

    private void cleanUpOrphan(String namespace, String objectId, String relation, String subjectNamespace, String subjectId) {
        try {
            guardClient.deleteTuple(namespace, objectId, relation, subjectNamespace, subjectId);
            log.warn("Rolled back: removed orphan Guard tuple {}:{}#{}", namespace, objectId, relation);
        } catch (RuntimeException e) {
            log.error("Rolled back but could not remove Guard tuple {}:{}#{} - needs manual cleanup", namespace, objectId, relation, e);
        }
    }

    private void deleteQuietly(String namespace, String objectId, String relation, String subjectNamespace, String subjectId) {
        try {
            guardClient.deleteTuple(namespace, objectId, relation, subjectNamespace, subjectId);
        } catch (RuntimeException e) {
            // 글/댓글은 이미 지워졌으므로 사용자 요청은 성공시키고, 남은 튜플은 추적할 수 있게 ERROR 로 남긴다.
            log.error("Committed but could not delete Guard tuple {}:{}#{} - stale permission remains", namespace, objectId, relation, e);
        }
    }
}
