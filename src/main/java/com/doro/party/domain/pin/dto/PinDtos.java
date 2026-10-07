package com.doro.party.domain.pin.dto;

import com.doro.party.domain.pin.entity.Pin;
import com.doro.party.domain.pin.entity.PinStatus;
import com.doro.party.domain.pin.entity.RevisitIntent;
import com.doro.party.domain.user.entity.PartyUser;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PinDtos {

    public static final int NAME_MAX = 100;
    public static final int MEMO_MAX = 2000;
    public static final int TAG_MAX = 30;
    /** 요청 크기 자체를 막는 하드 상한. 실제 허용 개수는 설정(party.limits.max-tags-per-pin)으로 검사한다. */
    public static final int TAGS_HARD_MAX = 50;
    public static final int RATING_MIN = 1;
    public static final int RATING_MAX = 5;

    private PinDtos() {
    }

    /** 핀 생성·수정 요청. 수정은 전체 교체다. */
    public record PinRequest(
            @NotBlank @Size(max = NAME_MAX) String name,
            @Size(max = MEMO_MAX) String sharedMemo,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lng,
            PinStatus status,
            @Min(RATING_MIN) @Max(RATING_MAX) Integer rating,
            RevisitIntent revisitIntent,
            @Size(max = TAGS_HARD_MAX) List<@Size(max = TAG_MAX * 2) String> tags
    ) {
    }

    public record PinResponse(
            UUID id,
            UUID mapId,
            UUID createdBy,
            String authorNickname,
            String authorColor,
            double lat,
            double lng,
            String name,
            String sharedMemo,
            PinStatus status,
            Integer rating,
            RevisitIntent revisitIntent,
            List<String> tags,
            long visitCount,
            LocalDate lastVisitedOn,
            long photoCount,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static PinResponse from(Pin pin, PinStats stats, PartyUser author) {
            return new PinResponse(pin.getId(), pin.getMapId(), pin.getCreatedBy(), author.getNickname(), author.getColor(), pin.getLat(), pin.getLng(),
                    pin.getName(), pin.getSharedMemo(), pin.getStatus(), pin.getRating(), pin.getRevisitIntent(),
                    pin.getTags().stream().sorted().toList(), stats.visitCount(), stats.lastVisitedOn(), stats.photoCount(),
                    pin.getCreatedAt(), pin.getUpdatedAt());
        }
    }

    /** 핀에 딸린 기록의 요약(방문 횟수·마지막 방문일·사진 수). */
    public record PinStats(long visitCount, LocalDate lastVisitedOn, long photoCount) {
        public static final PinStats EMPTY = new PinStats(0, null, 0);
    }
}
