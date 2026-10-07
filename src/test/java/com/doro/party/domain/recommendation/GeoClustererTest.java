package com.doro.party.domain.recommendation;

import com.doro.party.domain.recommendation.GeoClusterer.Cluster;
import com.doro.party.domain.recommendation.GeoClusterer.Point;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoClustererTest {

    /** 서울 부근에서 위도 0.001도는 약 110m, 경도 0.001도는 약 88m. */
    private static final double LAT = 37.5563;
    private static final double LNG = 126.9236;

    private static List<Integer> sizes(List<Cluster> clusters) {
        return clusters.stream().map(cluster -> cluster.indexes().size()).sorted(java.util.Comparator.reverseOrder()).toList();
    }

    @Test
    @DisplayName("반경 안의 점은 한 장소로, 먼 점은 따로 묶는다")
    void groupsByDistance() {
        List<Point> points = List.of(
                new Point(LAT, LNG), new Point(LAT + 0.0003, LNG),          // 약 33m
                new Point(LAT + 0.005, LNG),                                // 약 550m
                new Point(LAT, LNG + 0.02));                                // 약 1.7km

        assertThat(sizes(GeoClusterer.cluster(points, 80))).containsExactly(2, 1, 1);
    }

    @Test
    @DisplayName("반경 경계: 약 66m 는 80m 반경 안이고 약 110m 는 밖이다")
    void radiusBoundary() {
        assertThat(sizes(GeoClusterer.cluster(List.of(new Point(LAT, LNG), new Point(LAT + 0.0006, LNG)), 80))).containsExactly(2);
        assertThat(sizes(GeoClusterer.cluster(List.of(new Point(LAT, LNG), new Point(LAT + 0.001, LNG)), 80))).containsExactly(1, 1);
    }

    @Test
    @DisplayName("같은 좌표의 점은 모두 한 장소이고, 중심은 점들의 평균이다")
    void sameCoordinatesAndCenter() {
        List<Cluster> same = GeoClusterer.cluster(List.of(new Point(LAT, LNG), new Point(LAT, LNG), new Point(LAT, LNG)), 80);
        assertThat(same).hasSize(1);
        assertThat(same.get(0).indexes()).containsExactly(0, 1, 2);

        List<Cluster> two = GeoClusterer.cluster(List.of(new Point(LAT, LNG), new Point(LAT + 0.0004, LNG + 0.0004)), 80);
        assertThat(two).hasSize(1);
        assertThat(two.get(0).centerLat()).isCloseTo(LAT + 0.0002, within(1e-9));
        assertThat(two.get(0).centerLng()).isCloseTo(LNG + 0.0002, within(1e-9));
    }

    @Test
    @DisplayName("모든 점이 정확히 한 장소에 들어가고, 같은 입력은 항상 같은 결과다")
    void partitionAndDeterminism() {
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            points.add(new Point(LAT + ((i * 7919) % 1000) * 0.00002, LNG + ((i * 104729) % 1000) * 0.00002));
        }

        List<Cluster> first = GeoClusterer.cluster(points, 80);
        List<Cluster> second = GeoClusterer.cluster(points, 80);

        Set<Integer> seen = new HashSet<>();
        first.forEach(cluster -> cluster.indexes().forEach(index -> assertThat(seen.add(index)).as("점 %d 는 한 번만", index).isTrue()));
        assertThat(seen).hasSize(points.size());
        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("점이 없으면 빈 결과, 날짜 변경선(경도 음수)·남반구 좌표도 같은 규칙으로 묶는다")
    void edgeCases() {
        assertThat(GeoClusterer.cluster(List.of(), 80)).isEmpty();
        assertThat(sizes(GeoClusterer.cluster(List.of(new Point(-33.8688, 151.2093), new Point(-33.8689, 151.2094)), 80))).containsExactly(2);
        assertThat(sizes(GeoClusterer.cluster(List.of(new Point(40.7128, -74.0060), new Point(40.7129, -74.0061)), 80))).containsExactly(2);
    }
}
