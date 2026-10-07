package com.doro.party.domain.recommendation;

import com.doro.party.domain.pin.dto.PinDtos.PinResponse;
import com.doro.party.domain.pin.entity.PinStatus;
import com.doro.party.domain.pin.entity.RevisitIntent;
import com.doro.party.domain.recommendation.RecommendationDtos.PlaceAuthor;
import com.doro.party.domain.recommendation.RecommendationDtos.RecommendedPlace;
import com.doro.party.domain.recommendation.RecommendationDtos.ScoreBreakdown;
import com.doro.party.domain.recommendation.RecommendationProperties.Weights;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 핀들을 "장소" 로 묶고 점수를 매겨 순위를 만드는 순수 계산(저장소·권한과 무관). 어떤 장소가 왜 높은지 설명할 수 있도록
 * 점수를 항목별 내역(ScoreBreakdown)으로 함께 돌려준다.
 *
 * <p>규칙: 같은 장소에 핀을 꽂은 <b>사람</b> 한 명이 한 번씩 센다(같은 사람의 핀이 여러 개여도 한 명).
 * 한 사람의 입장은 하나로 합친다: 다녀온 핀이 하나라도 있으면 "다녀왔어요", 아니면 "가고 싶어요" / 재방문 의사는 "또 가고 싶어요" 가 하나라도 있으면 그것,
 * 아니면 "한 번이면 충분" / 평점은 남긴 평점의 평균(반올림).
 */
final class RecommendationEngine {

    private static final int MAX_OTHER_NAMES = 3;

    private RecommendationEngine() {
    }

    static List<RecommendedPlace> recommend(List<PinResponse> pins, Weights weights, int minPeople, int limit) {
        if (pins.isEmpty()) {
            return List.of();
        }
        List<GeoClusterer.Point> points = pins.stream().map(pin -> new GeoClusterer.Point(pin.lat(), pin.lng())).toList();
        List<Scored> scored = new ArrayList<>();
        for (GeoClusterer.Cluster cluster : GeoClusterer.cluster(points, weights.placeRadiusMeters())) {
            List<PinResponse> placePins = cluster.indexes().stream().map(pins::get).toList();
            Scored place = score(placePins, cluster, weights);
            if (place.people >= minPeople) {
                scored.add(place);
            }
        }
        scored.sort(Comparator
                .comparingInt((Scored place) -> place.score).reversed()
                .thenComparing(Comparator.comparingInt((Scored place) -> place.people).reversed())
                .thenComparing(Comparator.comparingDouble((Scored place) -> place.ratingAverageOrZero()).reversed())
                .thenComparing(place -> place.name));

        List<RecommendedPlace> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, scored.size()); i++) {
            result.add(scored.get(i).toPlace(i + 1));
        }
        return result;
    }

    private static Scored score(List<PinResponse> placePins, GeoClusterer.Cluster cluster, Weights w) {
        Map<UUID, List<PinResponse>> byAuthor = new LinkedHashMap<>();
        placePins.forEach(pin -> byAuthor.computeIfAbsent(pin.createdBy(), id -> new ArrayList<>()).add(pin));

        List<PlaceAuthor> authors = new ArrayList<>();
        int wishCount = 0;
        int visitedCount = 0;
        int againCount = 0;
        int onceCount = 0;
        int ratingPoints = 0;
        List<Integer> ratings = new ArrayList<>();
        for (List<PinResponse> mine : byAuthor.values()) {
            PinResponse first = mine.get(0);
            PinStatus status = mine.stream().anyMatch(pin -> pin.status() == PinStatus.VISITED) ? PinStatus.VISITED : PinStatus.WISH;
            RevisitIntent revisit = mine.stream().anyMatch(pin -> pin.revisitIntent() == RevisitIntent.AGAIN) ? RevisitIntent.AGAIN
                    : mine.stream().anyMatch(pin -> pin.revisitIntent() == RevisitIntent.ONCE) ? RevisitIntent.ONCE : null;
            Integer rating = mine.stream().map(PinResponse::rating).filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).average()
                    .stream().mapToObj(avg -> (int) Math.round(avg)).findFirst().orElse(null);

            if (status == PinStatus.WISH) {
                wishCount++;
            } else {
                visitedCount++;
            }
            if (revisit == RevisitIntent.AGAIN) {
                againCount++;
            } else if (revisit == RevisitIntent.ONCE) {
                onceCount++;
            }
            if (rating != null) {
                ratings.add(rating);
                ratingPoints += (rating - w.neutralRating()) * w.ratingPerStar();
            }
            authors.add(new PlaceAuthor(first.createdBy(), first.authorNickname(), first.authorColor(), status, rating, revisit));
        }

        int people = byAuthor.size();
        int peoplePoints = people * w.perPerson();
        int wishPoints = wishCount * w.wish();
        int visitedPoints = visitedCount * w.visited();
        int againPoints = againCount * w.revisitAgain();
        int oncePoints = onceCount * w.revisitOnce();
        Double ratingAverage = ratings.isEmpty() ? null : Math.round(ratings.stream().mapToInt(Integer::intValue).average().orElse(0) * 10) / 10.0;
        ScoreBreakdown breakdown = new ScoreBreakdown(people, peoplePoints, wishCount, wishPoints, visitedCount, visitedPoints,
                againCount, againPoints, onceCount, oncePoints, ratings.size(), ratingAverage, ratingPoints);
        int total = peoplePoints + wishPoints + visitedPoints + againPoints + oncePoints + ratingPoints;

        NameChoice names = chooseName(placePins);
        return new Scored(cluster.centerLat(), cluster.centerLng(), names.name, names.others, people, placePins.size(), total, breakdown, authors,
                placePins.stream().map(PinResponse::id).toList(), placePins.stream().map(PinResponse::mapId).distinct().toList());
    }

    private record NameChoice(String name, List<String> others) {
    }

    /** 가장 많이 쓰인 이름(같으면 먼저 꽂힌 것)을 장소 이름으로, 나머지 이름은 참고로 돌려준다. 대소문자·앞뒤 공백 차이는 같은 이름으로 본다. */
    private static NameChoice chooseName(List<PinResponse> placePins) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, String> original = new LinkedHashMap<>();
        for (PinResponse pin : placePins) {
            String key = pin.name().strip().toLowerCase(Locale.ROOT);
            counts.computeIfAbsent(key, k -> new int[]{0})[0]++;
            original.putIfAbsent(key, pin.name().strip());
        }
        List<String> ordered = new ArrayList<>(counts.keySet());
        ordered.sort(Comparator.comparingInt((String key) -> counts.get(key)[0]).reversed());
        String top = original.get(ordered.get(0));
        List<String> others = ordered.stream().skip(1).limit(MAX_OTHER_NAMES).map(original::get).toList();
        return new NameChoice(top, others);
    }

    private record Scored(double lat, double lng, String name, List<String> others, int people, int pinCount, int score,
                          ScoreBreakdown breakdown, List<PlaceAuthor> authors, List<UUID> pinIds, List<UUID> mapIds) {

        double ratingAverageOrZero() {
            return breakdown.ratingAverage() == null ? 0 : breakdown.ratingAverage();
        }

        RecommendedPlace toPlace(int rank) {
            return new RecommendedPlace(rank, lat, lng, name, others, people, pinCount, score, breakdown, authors, pinIds, mapIds);
        }
    }
}
