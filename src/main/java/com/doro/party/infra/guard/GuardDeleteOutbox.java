package com.doro.party.infra.guard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

/**
 * Guard 튜플 삭제 아웃박스의 저장 동작. 행 추가는 호출한 트랜잭션에 참여하고(없으면 자체 트랜잭션),
 * 커밋 뒤 콜백에서 쓰는 동작은 항상 새 트랜잭션에서 한다(이미 끝난 트랜잭션에 얹히면 커밋되지 않는다).
 */
@Slf4j
@Component
@EnableConfigurationProperties(GuardOutboxProperties.class)
public class GuardDeleteOutbox {

    private final GuardTupleDeleteOutboxRepository repository;
    private final GuardOutboxProperties properties;
    private final TransactionTemplate requiresNew;

    public GuardDeleteOutbox(GuardTupleDeleteOutboxRepository repository, GuardOutboxProperties properties, PlatformTransactionManager txManager) {
        this.repository = repository;
        this.properties = properties;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 호출한 트랜잭션 안에서 행을 추가한다. 그 트랜잭션이 롤백되면 행도 남지 않는다. */
    public UUID enqueue(String namespace, String objectId, String relation, String subjectNamespace, String subjectId, String subjectRelation) {
        return repository.save(new GuardTupleDeleteOutbox(namespace, objectId, relation, subjectNamespace, subjectId, subjectRelation, Instant.now())).getId();
    }

    /** 이미 한 번 실패한 삭제를 새 트랜잭션에서 행으로 남긴다(트랜잭션이 없거나 이미 끝난 경우). */
    public void enqueueFailed(String namespace, String objectId, String relation, String subjectNamespace, String subjectId, String subjectRelation, Exception cause) {
        requiresNew.executeWithoutResult(status -> {
            Instant now = Instant.now();
            GuardTupleDeleteOutbox row = new GuardTupleDeleteOutbox(namespace, objectId, relation, subjectNamespace, subjectId, subjectRelation, now);
            row.recordFailure(message(cause), now, properties.maxBackoff());
            repository.save(row);
        });
    }

    /** 삭제에 성공한 행을 지운다(새 트랜잭션). */
    public void complete(UUID id) {
        requiresNew.executeWithoutResult(status -> repository.deleteById(id));
    }

    /** 즉시 시도가 실패했음을 행에 기록한다(새 트랜잭션). 행이 이미 없으면(다른 인스턴스가 처리) 무시한다. */
    public void recordFailure(UUID id, Exception cause) {
        requiresNew.executeWithoutResult(status -> repository.findById(id)
                .ifPresent(row -> row.recordFailure(message(cause), Instant.now(), properties.maxBackoff())));
    }

    static String message(Exception e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String text = e.getClass().getSimpleName() + ": " + e.getMessage();
        return root == e ? text : text + " (cause: " + root.getClass().getSimpleName() + ": " + root.getMessage() + ")";
    }
}
