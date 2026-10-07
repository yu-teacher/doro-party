package com.doro.party.domain.recommendation;

import com.doro.party.domain.pin.entity.PinStatus;
import com.doro.party.domain.pin.entity.RevisitIntent;

import java.util.List;
import java.util.UUID;

public final class RecommendationDtos {

    private RecommendationDtos() {
    }

    /**
     * 점수가 어떻게 나왔는지의 내역. 각 항목은 "몇 명 x 가중치 = 점수" 이고 합이 장소의 점수다.
     * ratingAverage 는 평점을 남긴 사람들의 평균(없으면 null).
     */
    public record ScoreBreakdown(
            int people, int peoplePoints,
            int wishCount, int wishPoints,
            int visitedCount, int visitedPoints,
            int againCount, int againPoints,
            int onceCount, int oncePoints,
            int ratedCount, Double ratingAverage, int ratingPoints
    ) {
    }

    /** 이 장소에서 한 사람의 입장: 상태, 평점(여러 핀이면 평균), 재방문 의사. */
    public record PlaceAuthor(UUID userId, String nickname, String color, PinStatus status, Integer rating, RevisitIntent revisitIntent) {
    }

    public record RecommendedPlace(
            int rank,
            double lat,
            double lng,
            String name,
            List<String> otherNames,
            int people,
            int pinCount,
            int score,
            ScoreBreakdown breakdown,
            List<PlaceAuthor> authors,
            List<UUID> pinIds,
            List<UUID> mapIds
    ) {
    }

    /** @param mapIds 실제로 추천에 쓴 지도(요청한 것 중 내가 볼 수 있는 것) */
    public record RecommendationResponse(List<UUID> mapIds, List<RecommendedPlace> places) {
    }
}
