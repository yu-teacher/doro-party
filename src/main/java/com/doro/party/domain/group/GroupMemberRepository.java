package com.doro.party.domain.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMember.Key> {

    @Query("select m from GroupMember m where m.id.groupId = :groupId order by m.role desc, m.joinedAt asc")
    List<GroupMember> findByGroupId(@Param("groupId") UUID groupId);

    @Query("select m from GroupMember m where m.id.userId = :userId")
    List<GroupMember> findByUserId(@Param("userId") UUID userId);

    @Query("select count(m) from GroupMember m where m.id.groupId = :groupId")
    long countByGroupId(@Param("groupId") UUID groupId);

    @Query("select count(m) from GroupMember m where m.id.userId = :userId")
    long countByUserId(@Param("userId") UUID userId);

    interface GroupCount {
        UUID getGroupId();

        long getCount();
    }

    @Query("select m.id.groupId as groupId, count(m) as count from GroupMember m where m.id.groupId in :groupIds group by m.id.groupId")
    List<GroupCount> countsByGroupIds(@Param("groupIds") Collection<UUID> groupIds);
}
