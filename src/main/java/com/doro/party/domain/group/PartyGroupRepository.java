package com.doro.party.domain.group;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PartyGroupRepository extends JpaRepository<PartyGroup, UUID> {

    /** 멤버 수 상한처럼 "세고 넣는" 작업을 같은 모임에 대해 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from PartyGroup g where g.id = :id")
    Optional<PartyGroup> findByIdForUpdate(@Param("id") UUID id);
}
