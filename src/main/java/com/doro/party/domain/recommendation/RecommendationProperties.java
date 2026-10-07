package com.doro.party.domain.recommendation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 추천 점수의 가중치와 장소를 묶는 반경(party.recommendation.*). 규칙을 코드에 박지 않고 설정으로 두어,
 * 응답의 점수 내역과 함께 "왜 이 장소가 높은지"를 설명할 수 있게 한다.
 *
 * @param placeRadiusMeters 이 거리(미터) 안의 핀은 같은 장소로 본다
 * @param perPerson         그 장소에 핀을 꽂은 사람 한 명당 기본 점수(같은 사람이 여러 번 꽂아도 한 번만 센다)
 * @param wish              그 사람이 "가고 싶어요" 상태일 때 더하는 점수
 * @param visited           그 사람이 "다녀왔어요" 상태일 때 더하는 점수
 * @param revisitAgain      "또 가고 싶어요" 일 때 더하는 점수
 * @param revisitOnce       "한 번이면 충분" 일 때 더하는 점수(보통 음수)
 * @param ratingPerStar     평점이 중립 평점보다 별 하나 높을(낮을) 때마다 더하는(빼는) 점수
 * @param neutralRating     점수에 영향을 주지 않는 평점(보통 3)
 * @param maxResults        한 번에 돌려주는 장소 수의 상한
 */
@Configuration
@EnableConfigurationProperties(RecommendationProperties.Weights.class)
public class RecommendationProperties {

    @ConfigurationProperties(prefix = "party.recommendation")
    public record Weights(
            double placeRadiusMeters,
            int perPerson,
            int wish,
            int visited,
            int revisitAgain,
            int revisitOnce,
            int ratingPerStar,
            int neutralRating,
            int maxResults
    ) {
    }
}
