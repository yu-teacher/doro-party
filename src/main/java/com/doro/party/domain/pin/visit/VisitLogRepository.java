package com.doro.party.domain.pin.visit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VisitLogRepository extends JpaRepository<VisitLog, UUID> {

    List<VisitLog> findAllByPinIdOrderByVisitedOnDescCreatedAtDesc(UUID pinId);

    Optional<VisitLog> findByIdAndPinId(UUID id, UUID pinId);

    long countByPinId(UUID pinId);

    interface VisitStat {
        UUID getPinId();

        long getVisitCount();

        LocalDate getLastVisitedOn();
    }

    @Query("select v.pinId as pinId, count(v) as visitCount, max(v.visitedOn) as lastVisitedOn "
            + "from VisitLog v where v.pinId in :pinIds group by v.pinId")
    List<VisitStat> statsByPinIds(@Param("pinIds") Collection<UUID> pinIds);
}
