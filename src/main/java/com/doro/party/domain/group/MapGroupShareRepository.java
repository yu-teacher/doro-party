package com.doro.party.domain.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MapGroupShareRepository extends JpaRepository<MapGroupShare, MapGroupShare.Key> {

    @Query("select s from MapGroupShare s where s.id.mapId = :mapId order by s.createdAt")
    List<MapGroupShare> findByMapId(@Param("mapId") UUID mapId);

    @Query("select s from MapGroupShare s where s.id.groupId = :groupId order by s.createdAt")
    List<MapGroupShare> findByGroupId(@Param("groupId") UUID groupId);

    /** 이 모임에 내가 공유한 지도(탈퇴·내보내기 때 함께 거둔다). */
    @Query("select s from MapGroupShare s where s.id.groupId = :groupId and s.sharedBy = :userId")
    List<MapGroupShare> findByGroupIdAndSharedBy(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    @Query("select count(s) from MapGroupShare s where s.id.mapId = :mapId")
    long countByMapId(@Param("mapId") UUID mapId);

    /** 내가 속한 모임들에 공유된 지도(내가 모임 덕분에 볼 수 있는 지도). */
    @Query("select s from MapGroupShare s where s.id.groupId in (select m.id.groupId from GroupMember m where m.id.userId = :userId)")
    List<MapGroupShare> findVisibleTo(@Param("userId") UUID userId);

    interface GroupMapCount {
        UUID getGroupId();

        long getCount();
    }

    @Query("select s.id.groupId as groupId, count(s) as count from MapGroupShare s where s.id.groupId in :groupIds group by s.id.groupId")
    List<GroupMapCount> countsByGroupIds(@Param("groupIds") Collection<UUID> groupIds);
}
