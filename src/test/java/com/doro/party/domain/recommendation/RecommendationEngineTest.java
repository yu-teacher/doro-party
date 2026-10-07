package com.doro.party.domain.recommendation;

import com.doro.party.domain.pin.dto.PinDtos.PinResponse;
import com.doro.party.domain.pin.entity.PinStatus;
import com.doro.party.domain.pin.entity.RevisitIntent;
import com.doro.party.domain.recommendation.RecommendationDtos.RecommendedPlace;
import com.doro.party.domain.recommendation.RecommendationProperties.Weights;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 추천 점수는 손으로 계산한 기대값과 비교한다(가중치: 사람 10, 가고싶어요 +4, 다녀왔어요 +2, 또가고싶어요 +6, 한번이면충분 -4, 별 하나당 2점, 중립 3). */
class RecommendationEngineTest {

    private static final Weights WEIGHTS = new Weights(80, 10, 4, 2, 6, -4, 2, 3, 30);
    private static final double LAT = 37.5563;
    private static final double LNG = 126.9236;

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID CAROL = UUID.randomUUID();
    private static final UUID MAP = UUID.randomUUID();

    private static PinResponse pin(UUID author, String nickname, String name, double lat, double lng, PinStatus status, Integer rating, RevisitIntent revisit) {
        return new PinResponse(UUID.randomUUID(), MAP, author, nickname, "#E4572E", lat, lng, name, null, status, rating, revisit,
                List.of(), 0, null, 0, null, null);
    }

    private static PinResponse at(UUID author, String nickname, String name, double dLat, PinStatus status, Integer rating, RevisitIntent revisit) {
        return pin(author, nickname, name, LAT + dLat, LNG, status, rating, revisit);
    }

    @Test
    @DisplayName("사람 수가 많은 장소가 위에 오고, 점수 내역의 합이 점수와 같다")
    void moreOwnersRankHigher() {
        List<PinResponse> pins = List.of(
                // 장소 A(3명): 앨리스 가고싶어요, 밥 가고싶어요, 캐럴 다녀왔어요(5점, 또 가고싶어요)
                at(ALICE, "앨리스", "연남 파스타", 0.0000, PinStatus.WISH, null, null),
                at(BOB, "밥", "연남 파스타", 0.0001, PinStatus.WISH, null, null),
                at(CAROL, "캐럴", "연남 파스타", 0.0002, PinStatus.VISITED, 5, RevisitIntent.AGAIN),
                // 장소 B(1명, 멀리): 앨리스 다녀왔어요
                pin(ALICE, "앨리스", "한강 치킨", LAT + 0.02, LNG, PinStatus.VISITED, 4, null));

        List<RecommendedPlace> places = RecommendationEngine.recommend(pins, WEIGHTS, 1, 10);

        assertThat(places).hasSize(2);
        RecommendedPlace first = places.get(0);
        assertThat(first.rank()).isEqualTo(1);
        assertThat(first.name()).isEqualTo("연남 파스타");
        assertThat(first.people()).isEqualTo(3);
        // 사람 3x10=30 + 가고싶어요 2x4=8 + 다녀왔어요 1x2=2 + 또가고싶어요 1x6=6 + 평점 (5-3)x2=4 = 50
        assertThat(first.breakdown().peoplePoints()).isEqualTo(30);
        assertThat(first.breakdown().wishCount()).isEqualTo(2);
        assertThat(first.breakdown().wishPoints()).isEqualTo(8);
        assertThat(first.breakdown().visitedPoints()).isEqualTo(2);
        assertThat(first.breakdown().againPoints()).isEqualTo(6);
        assertThat(first.breakdown().ratingPoints()).isEqualTo(4);
        assertThat(first.breakdown().ratingAverage()).isEqualTo(5.0);
        assertThat(first.score()).isEqualTo(50);
        int sum = first.breakdown().peoplePoints() + first.breakdown().wishPoints() + first.breakdown().visitedPoints()
                + first.breakdown().againPoints() + first.breakdown().oncePoints() + first.breakdown().ratingPoints();
        assertThat(sum).isEqualTo(first.score());

        RecommendedPlace second = places.get(1);
        // 사람 1x10 + 다녀왔어요 2 + 평점 (4-3)x2 = 14
        assertThat(second.score()).isEqualTo(14);
        assertThat(second.rank()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 사람이 같은 장소에 여러 핀을 꽂아도 한 명으로 센다(입장은 하나로 합친다)")
    void samePersonCountsOnce() {
        List<PinResponse> pins = List.of(
                at(ALICE, "앨리스", "단골집", 0.0000, PinStatus.WISH, null, null),
                at(ALICE, "앨리스", "단골집", 0.0001, PinStatus.VISITED, 4, RevisitIntent.ONCE),
                at(ALICE, "앨리스", "단골집", 0.0002, PinStatus.VISITED, 5, RevisitIntent.AGAIN));

        RecommendedPlace place = RecommendationEngine.recommend(pins, WEIGHTS, 1, 10).get(0);

        assertThat(place.people()).isEqualTo(1);
        assertThat(place.pinCount()).isEqualTo(3);
        assertThat(place.authors()).hasSize(1);
        // 다녀왔어요(하나라도 다녀왔다면), 또 가고싶어요(AGAIN 이 하나라도 있으면), 평점은 평균(4,5)=4.5 를 반올림해 5
        assertThat(place.authors().get(0).status()).isEqualTo(PinStatus.VISITED);
        assertThat(place.authors().get(0).revisitIntent()).isEqualTo(RevisitIntent.AGAIN);
        assertThat(place.authors().get(0).rating()).isEqualTo(5);
        // 10 + 2 + 6 + (5-3)x2 = 22
        assertThat(place.score()).isEqualTo(22);
    }

    @Test
    @DisplayName("한 번이면 충분하다는 의견과 낮은 평점은 점수를 깎는다")
    void negativeSignalsLowerTheScore() {
        List<PinResponse> pins = List.of(at(BOB, "밥", "별로인 집", 0.0, PinStatus.VISITED, 1, RevisitIntent.ONCE));

        RecommendedPlace place = RecommendationEngine.recommend(pins, WEIGHTS, 1, 10).get(0);

        // 사람 10 + 다녀왔어요 2 + 한 번이면 충분 (-4) + 평점 (1-3)x2 = -4  ->  10 + 2 - 4 - 4 = 4
        assertThat(place.breakdown().oncePoints()).isEqualTo(-4);
        assertThat(place.breakdown().ratingPoints()).isEqualTo(-4);
        assertThat(place.score()).isEqualTo(4);
    }

    @Test
    @DisplayName("minPeople 로 거르고, limit 만큼만 돌려주고, 순위는 걸러진 목록 기준이다")
    void filteringAndLimit() {
        List<PinResponse> pins = List.of(
                at(ALICE, "앨리스", "둘이 찍은 곳", 0.0000, PinStatus.WISH, null, null),
                at(BOB, "밥", "둘이 찍은 곳", 0.0001, PinStatus.WISH, null, null),
                pin(ALICE, "앨리스", "혼자 1", LAT + 0.02, LNG, PinStatus.WISH, null, null),
                pin(BOB, "밥", "혼자 2", LAT + 0.04, LNG, PinStatus.WISH, null, null));

        assertThat(RecommendationEngine.recommend(pins, WEIGHTS, 1, 10)).hasSize(3);
        List<RecommendedPlace> twoOrMore = RecommendationEngine.recommend(pins, WEIGHTS, 2, 10);
        assertThat(twoOrMore).hasSize(1);
        assertThat(twoOrMore.get(0).rank()).isEqualTo(1);
        assertThat(RecommendationEngine.recommend(pins, WEIGHTS, 1, 2)).hasSize(2);
        assertThat(RecommendationEngine.recommend(pins, WEIGHTS, 5, 10)).isEmpty();
    }

    @Test
    @DisplayName("점수가 같으면 사람 수, 평점 평균, 이름 순으로 정렬한다")
    void tieBreakers() {
        List<PinResponse> pins = List.of(
                pin(ALICE, "앨리스", "나 가게", LAT, LNG, PinStatus.WISH, null, null),
                pin(BOB, "밥", "가 가게", LAT + 0.02, LNG, PinStatus.WISH, null, null),
                pin(CAROL, "캐럴", "다 가게", LAT + 0.04, LNG, PinStatus.WISH, null, null));

        List<RecommendedPlace> places = RecommendationEngine.recommend(pins, WEIGHTS, 1, 10);

        assertThat(places).extracting(RecommendedPlace::score).containsOnly(14);
        assertThat(places).extracting(RecommendedPlace::name).containsExactly("가 가게", "나 가게", "다 가게");
    }

    @Test
    @DisplayName("장소 이름은 가장 많이 쓰인 이름이고 나머지는 참고로 준다. 대소문자·공백 차이는 같은 이름이다")
    void nameSelection() {
        List<PinResponse> pins = List.of(
                at(ALICE, "앨리스", "Blue Bottle", 0.0000, PinStatus.WISH, null, null),
                at(BOB, "밥", "  blue bottle ", 0.0001, PinStatus.WISH, null, null),
                at(CAROL, "캐럴", "블루보틀 연남", 0.0002, PinStatus.WISH, null, null));

        RecommendedPlace place = RecommendationEngine.recommend(pins, WEIGHTS, 1, 10).get(0);

        assertThat(place.name()).isEqualTo("Blue Bottle");
        assertThat(place.otherNames()).containsExactly("블루보틀 연남");
        assertThat(place.pinIds()).hasSize(3);
        assertThat(place.lat()).isBetween(LAT, LAT + 0.0003);
    }

    @Test
    @DisplayName("핀이 없으면 추천도 없다")
    void empty() {
        assertThat(RecommendationEngine.recommend(List.of(), WEIGHTS, 1, 10)).isEmpty();
    }
}
