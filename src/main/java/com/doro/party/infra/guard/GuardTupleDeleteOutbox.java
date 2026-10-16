package com.doro.party.infra.guard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** 아직 Guard 에서 지우지 못한 관계 튜플. 지울 때까지(또는 이미 없다고 확인될 때까지) 다시 시도한다. */
@Entity
@Table(name = "guard_tuple_delete_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuardTupleDeleteOutbox {

    /** 마지막 오류 메시지를 이 길이로 자른다. */
    private static final int MAX_ERROR_LENGTH = 500;
    /** 첫 재시도 대기 시간. 실패할 때마다 두 배로 늘어난다. */
    static final Duration BASE_BACKOFF = Duration.ofSeconds(10);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String namespace;

    @Column(name = "object_id", nullable = false, length = 200)
    private String objectId;

    @Column(nullable = false, length = 100)
    private String relation;

    @Column(name = "subject_namespace", nullable = false, length = 100)
    private String subjectNamespace;

    @Column(name = "subject_id", nullable = false, length = 200)
    private String subjectId;

    @Column(name = "subject_relation", length = 100)
    private String subjectRelation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    public GuardTupleDeleteOutbox(String namespace, String objectId, String relation, String subjectNamespace, String subjectId,
                                  String subjectRelation, Instant now) {
        this.namespace = namespace;
        this.objectId = objectId;
        this.relation = relation;
        this.subjectNamespace = subjectNamespace;
        this.subjectId = subjectId;
        this.subjectRelation = subjectRelation;
        this.createdAt = now;
        // 커밋 직후 즉시 시도가 먼저 끝나도록, 재시도기는 첫 대기 시간이 지난 뒤에야 이 행을 집는다
        this.nextAttemptAt = now.plus(BASE_BACKOFF);
    }

    /** 실패를 기록하고 다음 시도 시각을 정한다: 10초 x 2^(실패 횟수-1), 최대 {@code maxBackoff}. */
    public void recordFailure(String error, Instant now, Duration maxBackoff) {
        attempts++;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), MAX_ERROR_LENGTH));
        nextAttemptAt = now.plus(backoff(attempts, maxBackoff));
    }

    static Duration backoff(int attempts, Duration maxBackoff) {
        long multiplier = 1L << Math.min(Math.max(attempts - 1, 0), 20);
        Duration delay = BASE_BACKOFF.multipliedBy(multiplier);
        return delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay;
    }

    /** 로그에 남길 사람이 읽는 표기: {@code ns:obj#rel@subjectNs:subject[#subjectRel]}. */
    public String describe() {
        return namespace + ":" + objectId + "#" + relation + "@" + subjectNamespace + ":" + subjectId
                + (subjectRelation == null ? "" : "#" + subjectRelation);
    }
}
