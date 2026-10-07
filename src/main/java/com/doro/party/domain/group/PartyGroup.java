package com.doro.party.domain.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/** 모임. 멤버십과 권한은 {@link GroupMember} 와 Guard(party_group) 가 관리한다. */
@Entity
@Table(name = "party_groups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartyGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PartyGroup(String name, UUID ownerId) {
        this.name = name;
        this.ownerId = ownerId;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeOwner(UUID ownerId) {
        this.ownerId = ownerId;
    }
}
