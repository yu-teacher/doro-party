package com.doro.party.domain.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class GroupDtos {

    public static final int NAME_MAX = 30;

    private GroupDtos() {
    }

    public record GroupNameRequest(@NotBlank @Size(max = NAME_MAX) String name) {
    }

    public record UserIdRequest(@NotNull UUID userId) {
    }

    public record GroupSummary(UUID id, String name, GroupRole myRole, int memberCount, int mapCount, String ownerNickname, Instant createdAt) {
    }

    /** 모임 멤버. 같은 모임 사람끼리는 닉네임과 색만 보이고 사용자명·이메일은 보이지 않는다. */
    public record GroupMemberView(UUID userId, String nickname, String color, GroupRole role, Instant joinedAt) {
    }

    public record GroupDetail(UUID id, String name, GroupRole myRole, List<GroupMemberView> members, int mapCount, Instant createdAt) {
    }

    public record GroupInvitePreview(String groupName, int memberCount, String ownerNickname, boolean alreadyMember, boolean full) {
    }

    public record JoinResult(UUID groupId, String groupName) {
    }

    /** 지도를 공유해 둔 모임. */
    public record MapGroupView(UUID groupId, String groupName, Instant sharedAt) {
    }
}
