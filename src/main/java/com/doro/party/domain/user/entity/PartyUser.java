package com.doro.party.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** 도로 파티 사용자. PK 는 Doro IAM 사용자 ID 와 같다. 이메일 등 개인정보는 저장하지 않는다. */
@DynamicUpdate
@Entity
@Table(name = "party_users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartyUser {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 50)
    private String nickname;

    /** 겹쳐보기에서 이 사람의 핀을 구분하는 색(#RRGGBB) */
    @Column(nullable = false, length = 7)
    private String color;

    public void updateProfile(String nickname, String username) {
        this.nickname = nickname;
        this.username = username;
    }

    /** Doro 계정이 영구 탈퇴해 익명화된 시각. 값이 있으면 이미 처리된 사용자다. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** 탈퇴 익명화: 식별 가능한 이름을 지운다. 행은 남겨 지도·핀·댓글이 가리키는 작성자 참조가 끊기지 않게 한다. */
    public void anonymize(String username, String nickname, Instant deletedAt) {
        this.username = username;
        this.nickname = nickname;
        this.deletedAt = deletedAt;
    }

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
