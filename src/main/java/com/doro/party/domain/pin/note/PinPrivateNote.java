package com.doro.party.domain.pin.note;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** 핀에 대한 나만의 메모. 핀과 사용자 한 쌍에 하나이고 쓴 사람에게만 보인다. */
@Entity
@Table(name = "pin_private_notes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PinPrivateNote {

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "pin_id", nullable = false)
        private UUID pinId;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        public static Key of(UUID pinId, UUID userId) {
            Key key = new Key();
            key.pinId = pinId;
            key.userId = userId;
            return key;
        }
    }

    @EmbeddedId
    private Key id;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
