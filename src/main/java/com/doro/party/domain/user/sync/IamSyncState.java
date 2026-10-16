package com.doro.party.domain.user.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Doro 와의 주기 동기화가 어디까지 진행됐는지(증분 조회 기준 시각)를 이름별로 보관한다. */
@Entity
@Table(name = "iam_sync_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IamSyncState {

    @Id
    @Column(length = 50)
    private String name;

    @Column(name = "cursor_at", nullable = false)
    private Instant cursorAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public IamSyncState(String name, Instant cursorAt) {
        this.name = name;
        this.cursorAt = cursorAt;
        this.updatedAt = Instant.now();
    }

    public void advanceTo(Instant cursorAt) {
        this.cursorAt = cursorAt;
        this.updatedAt = Instant.now();
    }
}
