package com.doro.party.infra.guard;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuardTupleDeleteOutboxRepository extends JpaRepository<GuardTupleDeleteOutbox, UUID> {

    /** 시도할 때가 된 행의 ID 를 오래된 순으로. */
    @Query("select o.id from GuardTupleDeleteOutbox o where o.nextAttemptAt <= :now order by o.createdAt asc, o.id asc")
    List<UUID> findDueIds(@Param("now") Instant now, Pageable page);

    /** 행을 잠가서 가져온다. 다른 인스턴스가 이미 잡은 행은 기다리지 않고 건너뛴다(FOR UPDATE SKIP LOCKED). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select o from GuardTupleDeleteOutbox o where o.id = :id")
    Optional<GuardTupleDeleteOutbox> lockById(@Param("id") UUID id);
}
