package com.doro.party.domain.map.repository;

import com.doro.party.domain.map.entity.PartyMap;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartyMapRepository extends JpaRepository<PartyMap, UUID> {

    List<PartyMap> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    long countByOwnerId(UUID ownerId);

    boolean existsByIdAndOwnerId(UUID id, UUID ownerId);

    /** 이 사람들이 친구 전체에게 공개해 둔 지도(친구 지도 둘러보기). */
    @Query("select m from PartyMap m where m.ownerId in :ownerIds and m.friendAccess <> com.doro.party.domain.share.FriendAccess.NONE order by m.name")
    List<PartyMap> findFriendVisible(@Param("ownerIds") java.util.Collection<UUID> ownerIds);

    /** 핀 개수 상한 검사처럼 "읽고 판단하고 쓰는" 작업을 같은 지도에 대해 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from PartyMap m where m.id = :id")
    Optional<PartyMap> findByIdForUpdate(@Param("id") UUID id);

    /**
     * 탈퇴 정리: 이 지도에 주인(탈퇴하는 사람) 말고 다른 사람이 남긴 핀·댓글·방문 기록·사진이 있는가.
     * 있으면 그 사람들의 기록이므로 지도를 지우지 않고 남긴다.
     */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM pins WHERE map_id = :mapId AND created_by <> :userId)
                OR EXISTS (SELECT 1 FROM pin_comments c JOIN pins p ON p.id = c.pin_id WHERE p.map_id = :mapId AND c.user_id <> :userId)
                OR EXISTS (SELECT 1 FROM visit_logs v JOIN pins p ON p.id = v.pin_id WHERE p.map_id = :mapId AND v.user_id <> :userId)
                OR EXISTS (SELECT 1 FROM pin_photos ph JOIN pins p ON p.id = ph.pin_id WHERE p.map_id = :mapId AND ph.uploaded_by <> :userId)
            """, nativeQuery = true)
    boolean hasContentFromOthers(@Param("mapId") UUID mapId, @Param("userId") UUID userId);
}
