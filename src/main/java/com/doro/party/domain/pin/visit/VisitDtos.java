package com.doro.party.domain.pin.visit;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class VisitDtos {

    public static final int NOTE_MAX = 500;

    private VisitDtos() {
    }

    public record VisitRequest(
            @NotNull LocalDate visitedOn,
            @Size(max = NOTE_MAX) String note
    ) {
    }

    public record VisitResponse(UUID id, UUID pinId, UUID userId, LocalDate visitedOn, String note, Instant createdAt) {
        public static VisitResponse from(VisitLog visit) {
            return new VisitResponse(visit.getId(), visit.getPinId(), visit.getUserId(), visit.getVisitedOn(), visit.getNote(), visit.getCreatedAt());
        }
    }
}
