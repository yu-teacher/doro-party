package com.doro.party.domain.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface PushPreferencesRepository extends JpaRepository<PushPreferences, UUID> {

    @Transactional
    @Modifying
    @Query("delete from PushPreferences p where p.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
