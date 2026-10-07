package com.doro.party.domain.pin.repository;

import com.doro.party.domain.pin.entity.Pin;
import com.doro.party.domain.pin.entity.PinStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PinRepository extends JpaRepository<Pin, UUID> {

    /** 다른 지도의 핀 ID 로 접근하는 것(IDOR)을 막기 위해 항상 지도 ID 와 함께 찾는다. */
    Optional<Pin> findByIdAndMapId(UUID id, UUID mapId);

    long countByMapId(UUID mapId);

    @Query("select p from Pin p where p.mapId = :mapId "
            + "and (:status is null or p.status = :status) "
            + "and (:tag is null or :tag member of p.tags) "
            + "order by p.createdAt asc, p.id asc")
    List<Pin> search(@Param("mapId") UUID mapId, @Param("status") PinStatus status, @Param("tag") String tag);

    interface PinCount {
        UUID getMapId();

        long getCount();
    }

    @Query("select p.mapId as mapId, count(p) as count from Pin p where p.mapId in :mapIds group by p.mapId")
    List<PinCount> countByMapIds(@Param("mapIds") Collection<UUID> mapIds);
}
