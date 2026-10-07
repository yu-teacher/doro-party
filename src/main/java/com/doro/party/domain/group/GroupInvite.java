package com.doro.party.domain.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** 모임마다 하나인 초대 링크. 코드는 해시로 찾고, 다시 보여 줄 수 있게 암호화한 원문을 둔다. */
@Entity
@Table(name = "group_invites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupInvite {

    @Id
    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "code_enc", nullable = false)
    private String codeEnc;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
