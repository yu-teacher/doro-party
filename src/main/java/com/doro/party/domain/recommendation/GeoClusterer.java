package com.doro.party.domain.recommendation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 위경도의 점들을 거리(미터) 기준으로 묶는다. 점을 순서대로 보며 중심이 반경 안에 있는 가장 가까운 묶음에 넣고, 없으면 새 묶음을 만든다.
 * 같은 입력 순서면 항상 같은 결과다(결정적). 도시 규모에서 오차가 무시되는 등장방형 근사로 미터 좌표를 만들고, 격자로 이웃만 살펴 빠르다.
 * 모든 점은 정확히 한 묶음에 속한다.
 */
final class GeoClusterer {

    private static final double METERS_PER_DEGREE_LAT = 110_540.0;
    private static final double METERS_PER_DEGREE_LNG_AT_EQUATOR = 111_320.0;

    record Point(double lat, double lng) {
    }

    /** 묶음 하나: 속한 점의 입력 순서 번호와 중심(위경도 평균). */
    record Cluster(List<Integer> indexes, double centerLat, double centerLng) {
    }

    private GeoClusterer() {
    }

    static List<Cluster> cluster(List<Point> points, double radiusMeters) {
        if (points.isEmpty()) {
            return List.of();
        }
        double referenceLat = points.stream().mapToDouble(Point::lat).average().orElse(0);
        double metersPerLng = METERS_PER_DEGREE_LNG_AT_EQUATOR * Math.cos(Math.toRadians(referenceLat));

        List<double[]> meters = new ArrayList<>(points.size());
        for (Point point : points) {
            meters.add(new double[]{point.lng() * metersPerLng, point.lat() * METERS_PER_DEGREE_LAT});
        }

        class Working {
            final List<Integer> indexes = new ArrayList<>();
            double sumX;
            double sumY;
            String cell;
        }
        List<Working> clusters = new ArrayList<>();
        Map<String, Set<Integer>> grid = new HashMap<>();

        for (int i = 0; i < points.size(); i++) {
            double x = meters.get(i)[0];
            double y = meters.get(i)[1];
            long cx = (long) Math.floor(x / radiusMeters);
            long cy = (long) Math.floor(y / radiusMeters);
            int nearest = -1;
            double nearestDistance = radiusMeters;
            for (long dx = -1; dx <= 1; dx++) {
                for (long dy = -1; dy <= 1; dy++) {
                    for (int candidate : grid.getOrDefault((cx + dx) + "," + (cy + dy), Set.of())) {
                        Working other = clusters.get(candidate);
                        double distance = Math.hypot(other.sumX / other.indexes.size() - x, other.sumY / other.indexes.size() - y);
                        if (distance <= nearestDistance) {
                            nearest = candidate;
                            nearestDistance = distance;
                        }
                    }
                }
            }
            int target = nearest;
            if (target == -1) {
                clusters.add(new Working());
                target = clusters.size() - 1;
            }
            Working working = clusters.get(target);
            working.indexes.add(i);
            working.sumX += x;
            working.sumY += y;
            // 중심이 움직여 칸이 바뀌었다면 격자에서 옮긴다
            double centerX = working.sumX / working.indexes.size();
            double centerY = working.sumY / working.indexes.size();
            String cell = (long) Math.floor(centerX / radiusMeters) + "," + (long) Math.floor(centerY / radiusMeters);
            if (!cell.equals(working.cell)) {
                if (working.cell != null) {
                    grid.get(working.cell).remove(target);
                }
                working.cell = cell;
                grid.computeIfAbsent(cell, key -> new HashSet<>()).add(target);
            }
        }

        List<Cluster> result = new ArrayList<>();
        for (Working working : clusters) {
            double lat = working.indexes.stream().mapToDouble(index -> points.get(index).lat()).average().orElse(0);
            double lng = working.indexes.stream().mapToDouble(index -> points.get(index).lng()).average().orElse(0);
            result.add(new Cluster(List.copyOf(working.indexes), lat, lng));
        }
        return result;
    }
}
