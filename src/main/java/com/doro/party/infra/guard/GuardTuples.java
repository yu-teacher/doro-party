package com.doro.party.infra.guard;

import com.hunnit_beasts.doro.sdk.client.DoroGuardClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * DB 트랜잭션과 Guard(원격) 관계 튜플의 일관성을 맞추는 래퍼.
 *
 * <p>Guard 는 같은 트랜잭션에 참여할 수 없으므로 방향별로 안전한 쪽을 택한다.
 * <ul>
 *   <li><b>쓰기</b>: 트랜잭션 안에서 즉시 쓰되 실패하면 예외를 던져 DB 도 롤백한다. 튜플 없이 글만 생기면
 *       작성자가 자기 글을 수정하지 못하기 때문이다. 이후 DB 가 롤백되면 이미 쓴 튜플을 정리한다.</li>
 *   <li><b>삭제</b>: 커밋이 확정된 뒤에 지운다. 먼저 지웠는데 DB 삭제가 실패하면 글은 남고 권한만 사라진다.
 *       지울 튜플은 같은 트랜잭션에서 아웃박스({@link GuardTupleDeleteOutbox})에 기록하므로, 커밋 직후 Guard 가 잠깐 죽어 있어도
 *       {@link GuardDeleteOutboxRetrier} 가 성공할 때까지 다시 지운다(권한 회수가 조용히 사라지지 않는다).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuardTuples {

    private final DoroGuardClient guardClient;
    private final GuardDeleteOutbox outbox;

    /** 관계 튜플을 쓴다. Guard 쓰기가 실패하면 예외가 전파되어 호출한 트랜잭션이 롤백된다. */
    public void write(String namespace, String objectId, String relation, String subjectNamespace, String subjectId) {
        write(namespace, objectId, relation, subjectNamespace, subjectId, null);
    }

    /**
     * 사용자 집합을 주체로 하는 튜플을 쓴다. 예: {@code party_map:M#viewer@party_group:G#member} 는
     * "모임 G 의 멤버는 지도 M 의 viewer" 라는 뜻이라, 멤버가 들고 나도 지도 쪽 튜플을 고칠 필요가 없다.
     */
    public void write(String namespace, String objectId, String relation, String subjectNamespace, String subjectId, String subjectRelation) {
        guardClient.writeTupleOrThrow(namespace, objectId, relation, subjectNamespace, subjectId, subjectRelation);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        cleanUpOrphan(namespace, objectId, relation, subjectNamespace, subjectId, subjectRelation);
                    }
                }
            });
        }
    }

    /** 현재 트랜잭션이 커밋된 뒤에 튜플을 지운다. 트랜잭션이 없으면 바로 지운다. 실패하면 아웃박스에 남겨 다시 지운다. */
    public void deleteAfterCommit(String namespace, String objectId, String relation, String subjectNamespace, String subjectId) {
        deleteAfterCommit(namespace, objectId, relation, subjectNamespace, subjectId, null);
    }

    /**
     * {@link #write(String, String, String, String, String, String)} 로 쓴 사용자 집합 튜플을 포함해, 현재 트랜잭션이 커밋된 뒤에 튜플을 지운다. 트랜잭션이 있으면 지울 튜플을 같은 트랜잭션에서 아웃박스에 기록하고(롤백되면 함께 사라진다),
     * 커밋 뒤 바로 지워 보고 성공하면 기록을 지운다. 실패하면 사용자 요청은 이미 커밋됐으므로 예외 없이 기록을 남겨 재시도기에 맡긴다.
     * 트랜잭션이 없으면 바로 지우고, 실패했을 때만 기록한다.
     */
    public void deleteAfterCommit(String namespace, String objectId, String relation, String subjectNamespace, String subjectId, String subjectRelation) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            try {
                guardClient.deleteTupleOrThrow(namespace, objectId, relation, subjectNamespace, subjectId, subjectRelation);
            } catch (RuntimeException e) {
                log.warn("Guard tuple delete failed, queued for retry: {}:{}#{}", namespace, objectId, relation);
                outbox.enqueueFailed(namespace, objectId, relation, subjectNamespace, subjectId, subjectRelation, e);
            }
            return;
        }
        UUID outboxId = outbox.enqueue(namespace, objectId, relation, subjectNamespace, subjectId, subjectRelation);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                attemptNow(outboxId, namespace, objectId, relation, subjectNamespace, subjectId, subjectRelation);
            }
        });
    }

    /**
     * 커밋을 기다리지 않고 바로 지우며, 실패하면 예외를 던진다. 탈퇴 정리처럼 "지우지 못했으면 처리 전체를 실패시키고 다시 시도해야" 하는
     * 경로에서만 쓴다. 이미 없는 튜플을 지우는 것은 오류가 아니라 반복해도 안전하다.
     */
    public void deleteOrThrow(String namespace, String objectId, String relation, String subjectNamespace, String subjectId, String subjectRelation) {
        guardClient.deleteTupleOrThrow(namespace, objectId, relation, subjectNamespace, subjectId, subjectRelation);
    }

    private void cleanUpOrphan(String namespace, String objectId, String relation, String subjectNamespace, String subjectId, String subjectRelation) {
        try {
            guardClient.deleteTupleOrThrow(namespace, objectId, relation, subjectNamespace, subjectId, subjectRelation);
            log.warn("Rolled back: removed orphan Guard tuple {}:{}#{}", namespace, objectId, relation);
        } catch (RuntimeException e) {
            // 롤백된 트랜잭션에는 기록할 수 없으므로 새 트랜잭션으로 아웃박스에 남겨 재시도기가 지우게 한다.
            log.warn("Rolled back but could not remove Guard tuple {}:{}#{}, queued for retry", namespace, objectId, relation);
            enqueueQuietly(namespace, objectId, relation, subjectNamespace, subjectId, subjectRelation, e);
        }
    }

    /** 커밋 직후의 즉시 삭제. 성공하면 아웃박스 행을 지우고, 실패하면 행을 남겨 둔다(예외는 던지지 않는다). */
    private void attemptNow(UUID outboxId, String namespace, String objectId, String relation, String subjectNamespace, String subjectId, String subjectRelation) {
        try {
            guardClient.deleteTupleOrThrow(namespace, objectId, relation, subjectNamespace, subjectId, subjectRelation);
        } catch (RuntimeException e) {
            log.warn("Committed but Guard tuple delete failed, queued for retry: {}:{}#{}", namespace, objectId, relation);
            try {
                outbox.recordFailure(outboxId, e);
            } catch (RuntimeException ignored) {
                // 실패 기록이 안 돼도 행은 남아 있어 재시도기가 처리한다.
            }
            return;
        }
        try {
            outbox.complete(outboxId);
        } catch (RuntimeException e) {
            // 튜플은 지웠고 행만 남았다. 재시도기가 한 번 더 지우면(없는 튜플 삭제는 안전) 행도 정리된다.
            log.warn("Guard tuple deleted but outbox row {} could not be removed; the retrier will clear it", outboxId);
        }
    }

    private void enqueueQuietly(String namespace, String objectId, String relation, String subjectNamespace, String subjectId, String subjectRelation, Exception cause) {
        try {
            outbox.enqueueFailed(namespace, objectId, relation, subjectNamespace, subjectId, subjectRelation, cause);
        } catch (RuntimeException e) {
            log.error("Could not queue Guard tuple delete {}:{}#{} - needs manual cleanup", namespace, objectId, relation, e);
        }
    }
}
