package com.doro.party.domain.share;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MapUserShareRepository extends JpaRepository<MapUserShare, MapUserShare.Key> {

    @Query("select s from MapUserShare s where s.id.mapId = :mapId order by s.createdAt")
    List<MapUserShare> findByMapId(@Param("mapId") UUID mapId);

    /** 나에게 공유된 지도. */
    @Query("select s from MapUserShare s where s.id.userId = :userId")
    List<MapUserShare> findByUserId(@Param("userId") UUID userId);

    @Query("select count(s) from MapUserShare s where s.id.mapId = :mapId")
    long countByMapId(@Param("mapId") UUID mapId);

    /** 내가 만든 지도 중 이 사람에게 공유한 것(친구를 끊을 때 회수한다). */
    @Query("select s from MapUserShare s where s.id.userId = :recipientId "
            + "and s.id.mapId in (select m.id from PartyMap m where m.ownerId = :ownerId)")
    List<MapUserShare> findGivenTo(@Param("ownerId") UUID ownerId, @Param("recipientId") UUID recipientId);
}
