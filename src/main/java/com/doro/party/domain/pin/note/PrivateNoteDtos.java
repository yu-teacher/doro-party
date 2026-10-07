package com.doro.party.domain.pin.note;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class PrivateNoteDtos {

    public static final int BODY_MAX = 2000;

    private PrivateNoteDtos() {
    }

    public record PrivateNoteRequest(@NotBlank @Size(max = BODY_MAX) String body) {
    }

    public record PrivateNoteResponse(UUID pinId, String body, Instant updatedAt) {
        public static PrivateNoteResponse from(PinPrivateNote note) {
            return new PrivateNoteResponse(note.getId().getPinId(), note.getBody(), note.getUpdatedAt());
        }
    }
}
