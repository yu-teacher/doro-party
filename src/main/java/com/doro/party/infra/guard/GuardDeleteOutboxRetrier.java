package com.doro.party.infra.guard;

import com.hunnit_beasts.doro.sdk.client.DoroGuardClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 아웃박스에 쌓인 Guard 튜플 삭제를 주기적으로 다시 시도한다. 시도할 때가 된 행을 오래된 순으로 한 번에 {@code batch-size} 개까지 처리한다.
 * 행마다 짧은 트랜잭션으로 행을 잠근 채(다른 인스턴스가 잡은 행은 건너뜀) 삭제하므로 인스턴스가 여럿이어도 안전하고,
 * 이미 없는 튜플을 지우는 것은 오류가 아니라서 중복 시도가 있어도 문제없다.
 */
@Slf4j
@Component
public class GuardDeleteOutboxRetrier {

    /** 이 횟수째 실패하면 운영자가 알아차리도록 ERROR 를 한 번 남긴다. */
    static final int ALERT_ATTEMPTS = 10;

    private final GuardTupleDeleteOutboxRepository repository;
    private final DoroGuardClient guardClient;
    private final GuardOutboxProperties properties;
    private final TransactionTemplate tx;

    public GuardDeleteOutboxRetrier(GuardTupleDeleteOutboxRepository repository, DoroGuardClient guardClient,
                                    GuardOutboxProperties properties, PlatformTransactionManager txManager) {
        this.repository = repository;
        this.guardClient = guardClient;
        this.properties = properties;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 처리 결과 요약. */
    public record Result(int succeeded, int failed) {
    }

    @Scheduled(fixedDelayString = "${party.guard-outbox.interval:PT30S}", initialDelayString = "${party.guard-outbox.interval:PT30S}")
    public void run() {
        if (!properties.enabled()) {
            return;
        }
        try {
            processDue(Instant.now());
        } catch (RuntimeException e) {
            log.warn("Guard delete outbox run failed: {}", e.toString());
        }
    }

    /** 기준 시각 {@code now} 에 시도할 때가 된 행을 처리한다. 설정이 꺼져 있으면 아무것도 하지 않는다. */
    public Result processDue(Instant now) {
        if (!properties.enabled()) {
            return new Result(0, 0);
        }
        List<UUID> ids = repository.findDueIds(now, PageRequest.of(0, properties.batchSize()));
        int succeeded = 0;
        int failed = 0;
        String firstError = null;
        for (UUID id : ids) {
            Outcome outcome = tx.execute(status -> attempt(id, now));
            if (outcome == null || outcome.skipped()) {
                continue;
            }
            if (outcome.error() == null) {
                succeeded++;
            } else {
                failed++;
                if (firstError == null) {
                    firstError = outcome.error();
                }
            }
        }
        if (failed > 0) {
            log.warn("Guard delete outbox: {} retried ok, {} failed (will back off); first error: {}", succeeded, failed, firstError);
        }
        return new Result(succeeded, failed);
    }

    private record Outcome(boolean skipped, String error) {
        static final Outcome SKIPPED = new Outcome(true, null);
        static final Outcome OK = new Outcome(false, null);
    }

    private Outcome attempt(UUID id, Instant now) {
        GuardTupleDeleteOutbox row = repository.lockById(id).orElse(null);
        if (row == null || row.getNextAttemptAt().isAfter(now)) {
            return Outcome.SKIPPED; // 다른 인스턴스가 처리했거나, 그 사이에 다음 시도 시각이 미뤄졌다
        }
        try {
            guardClient.deleteTupleOrThrow(row.getNamespace(), row.getObjectId(), row.getRelation(),
                    row.getSubjectNamespace(), row.getSubjectId(), row.getSubjectRelation());
        } catch (RuntimeException e) {
            row.recordFailure(GuardDeleteOutbox.message(e), now, properties.maxBackoff());
            if (row.getAttempts() == ALERT_ATTEMPTS) {
                log.error("Guard tuple delete still failing after {} attempts - stale permission remains: {} (inspect guard_tuple_delete_outbox)",
                        row.getAttempts(), row.describe());
            }
            return new Outcome(false, row.getLastError());
        }
        if (row.getAttempts() > 0) {
            log.info("Guard tuple delete recovered after {} failed attempts: {}", row.getAttempts(), row.describe());
        }
        repository.delete(row);
        return Outcome.OK;
    }
}
