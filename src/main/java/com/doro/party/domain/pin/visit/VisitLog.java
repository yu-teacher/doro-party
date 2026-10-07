package com.doro.party.domain.pin.visit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** 핀을 다녀온 기록 한 번: 날짜와 한 줄 후기. 같은 핀에 여러 번 쌓인다. */
@Entity
@Table(name = "visit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "pin_id", nullable = false, updatable = false)
    private UUID pinId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "visited_on", nullable = false)
    private LocalDate visitedOn;

    @Column(length = 500)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private VisitLog(UUID pinId, UUID userId, LocalDate visitedOn, String note) {
        this.pinId = pinId;
        this.userId = userId;
        this.visitedOn = visitedOn;
        this.note = note;
    }
}
