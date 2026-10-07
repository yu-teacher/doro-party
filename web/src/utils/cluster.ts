export interface PixelPoint {
  id: string;
  x: number;
  y: number;
}

export interface PixelCluster {
  ids: string[];
  /** 묶음의 중심(속한 점들의 평균 위치) */
  x: number;
  y: number;
}

const NOTHING: ReadonlySet<string> = new Set();

/**
 * 화면 위 픽셀 거리가 가까운 점들을 묶는다. 지도를 확대하면 점 사이 픽셀 거리가 늘어 묶음이 풀리고, 줄이면 합쳐진다.
 * 점을 순서대로 보며 중심이 threshold 안에 있는 가장 가까운 묶음에 넣고, 없으면 새 묶음을 만든다(격자로 이웃만 살펴 빠르다).
 * keepSingle 에 든 점(선택된 핀 등)은 어떤 묶음에도 들어가지 않고 혼자 남는다.
 * 모든 점은 정확히 한 묶음에 속한다.
 */
export function clusterPoints(points: ReadonlyArray<PixelPoint>, threshold: number, keepSingle: ReadonlySet<string> = NOTHING): PixelCluster[] {
  interface Working {
    ids: string[];
    sumX: number;
    sumY: number;
    cell: string | null;
  }
  const clusters: Working[] = [];
  const grid = new Map<string, Set<number>>();
  const cellOf = (x: number, y: number) => `${Math.floor(x / threshold)},${Math.floor(y / threshold)}`;
  const centerOf = (cluster: Working) => ({ x: cluster.sumX / cluster.ids.length, y: cluster.sumY / cluster.ids.length });

  const place = (index: number) => {
    const cluster = clusters[index];
    const center = centerOf(cluster);
    const cell = cellOf(center.x, center.y);
    if (cluster.cell === cell) {
      return;
    }
    if (cluster.cell !== null) {
      grid.get(cluster.cell)?.delete(index);
    }
    cluster.cell = cell;
    const members = grid.get(cell) ?? new Set<number>();
    members.add(index);
    grid.set(cell, members);
  };

  for (const point of points) {
    if (keepSingle.has(point.id)) {
      clusters.push({ ids: [point.id], sumX: point.x, sumY: point.y, cell: null });
      continue;
    }
    const cx = Math.floor(point.x / threshold);
    const cy = Math.floor(point.y / threshold);
    let nearest = -1;
    let nearestDistance = threshold;
    for (let dx = -1; dx <= 1; dx += 1) {
      for (let dy = -1; dy <= 1; dy += 1) {
        for (const index of grid.get(`${cx + dx},${cy + dy}`) ?? []) {
          const center = centerOf(clusters[index]);
          const distance = Math.hypot(center.x - point.x, center.y - point.y);
          if (distance <= nearestDistance) {
            nearest = index;
            nearestDistance = distance;
          }
        }
      }
    }
    if (nearest === -1) {
      clusters.push({ ids: [point.id], sumX: point.x, sumY: point.y, cell: null });
      place(clusters.length - 1);
    } else {
      clusters[nearest].ids.push(point.id);
      clusters[nearest].sumX += point.x;
      clusters[nearest].sumY += point.y;
      place(nearest);
    }
  }
  return clusters.map((cluster) => ({ ids: cluster.ids, ...centerOf(cluster) }));
}
