import { useEffect, useImperativeHandle, useRef, useState } from 'react';
import type { Ref } from 'react';
import type { Pin } from '../api/types';
import { DEFAULT_MAP_CENTER, DEFAULT_MAP_LEVEL, KAKAO_MAP_APP_KEY } from '../config';
import { clusterPoints } from '../utils/cluster';
import { heatStyle } from './heat';
import type { HeatSpot } from './heat';
import { loadKakaoMaps } from './loadKakaoMaps';
import { DRAFT_COLOR, markerIcon, pinColor, safeColor } from './markerIcon';
import type { ColorMode, MarkerIconSpec } from './markerIcon';

type Status = 'loading' | 'ready' | 'error' | 'no-key';

export interface LatLngLiteral {
  lat: number;
  lng: number;
}

/** 부모가 지도에서 읽어 가는 값. 지도 객체 자체는 밖으로 내보내지 않는다. */
export interface PartyMapHandle {
  /** 지금 화면 중앙의 좌표. 지도가 아직 준비되지 않았으면 null. */
  getCenter: () => LatLngLiteral | null;
}

interface Props {
  ref?: Ref<PartyMapHandle>;
  pins: Pin[];
  selectedPinId: string | null;
  /** 아직 저장하지 않은, 방금 눌러 놓은 위치 */
  draft: LatLngLiteral | null;
  /** 이 값이 바뀌면(그리고 null 이 아니면) 핀이 모두 보이도록 지도를 맞춘다. 핀이 불러와진 뒤의 키를 넘긴다. */
  fitKey: string | null;
  /**
   * nonce 가 바뀔 때마다 그 위치로 지도를 옮긴다(내 위치 버튼 등).
   * aboveSheet 이면 곧 하단 시트가 아래쪽을 덮으므로, 시트에 가려지지 않는 위쪽 영역의 가운데에 그 위치가 오도록 옮긴다.
   */
  panTo: (LatLngLiteral & { nonce: number; aboveSheet?: boolean }) | null;
  /** 마커 색: 핀의 상태로 / 핀을 꽂은 사람으로(겹쳐보기). 겹쳐보기에서는 가고 싶은 곳을 옅게 그린다. */
  colorMode: ColorMode;
  /** 추천 장소를 점수가 높을수록 진한 붉은 원으로 겹쳐 그린다(히트맵). 없으면 그리지 않는다. */
  heat: { spots: HeatSpot[]; min: number; max: number } | null;
  onMapClick: (position: LatLngLiteral) => void;
  onPinClick: (pinId: string) => void;
  /** 더 확대해도 풀리지 않는 묶음(같은 장소에 여러 핀)을 눌렀을 때 */
  onClusterOpen: (pins: Pin[]) => void;
}

const SINGLE_PIN_LEVEL = 3;
/** 하단 시트(Sheet)의 최대 높이 비율(max-h-[65%])과 같다. 시트가 열리면 지도의 아래쪽 이 비율만큼이 가려진다. */
const SHEET_COVER_RATIO = 0.65;
/** 지도 위에 떠 있는 컨트롤(지도 선택 바·필터 칩)이 지도 위쪽을 가리는 높이(px). 대략값이다. */
const FLOATING_CONTROLS_PX = 110;
/** 화면에서 이 거리(px) 안의 핀은 하나로 묶는다 */
const CLUSTER_PX = 44;
/** 묶음 안의 점들이 이 정도(px) 안에 모여 있으면 확대로는 풀리지 않는 같은 장소로 본다 */
const SAME_PLACE_PX = 3;
const CLUSTER_SIZE = 44;
const CLUSTER_RING = 5;

function toImage(icon: MarkerIconSpec): kakao.maps.MarkerImage {
  return new kakao.maps.MarkerImage(icon.url, new kakao.maps.Size(icon.width, icon.height), {
    offset: new kakao.maps.Point(icon.width / 2, icon.height),
  });
}

/** 묶음 안 핀들의 색 비율대로 나눈 고리(conic-gradient). 색은 #RRGGBB 로 검증한 값만 스타일에 넣는다. */
function ringBackground(pins: Pin[], mode: ColorMode): string {
  const counts = new Map<string, number>();
  pins.forEach((pin) => {
    const color = pinColor(pin, mode);
    counts.set(color, (counts.get(color) ?? 0) + 1);
  });
  const entries = [...counts.entries()].sort((a, b) => b[1] - a[1]);
  if (entries.length === 1) {
    return safeColor(entries[0][0]);
  }
  let from = 0;
  const stops = entries.map(([color, count]) => {
    const to = from + (count / pins.length) * 100;
    const stop = `${safeColor(color)} ${from.toFixed(2)}% ${to.toFixed(2)}%`;
    from = to;
    return stop;
  });
  return `conic-gradient(${stops.join(', ')})`;
}

/** 묶음 마커: 바깥 고리는 그 안에 든 핀의 색(작성자), 가운데는 핀 개수. */
function buildClusterElement(pins: Pin[], mode: ColorMode, onClick: () => void): HTMLElement {
  const outer = document.createElement('button');
  outer.type = 'button';
  outer.setAttribute('aria-label', `핀 ${pins.length}개 묶음, 누르면 확대해요`);
  outer.style.cssText = `width:${CLUSTER_SIZE}px;height:${CLUSTER_SIZE}px;padding:${CLUSTER_RING}px;border:0;border-radius:50%;cursor:pointer;`
    + `background:${ringBackground(pins, mode)};box-shadow:0 2px 6px rgba(0,0,0,.45);`;
  const inner = document.createElement('span');
  inner.style.cssText = 'display:flex;align-items:center;justify-content:center;width:100%;height:100%;border-radius:50%;'
    + 'background:#0F172A;color:#F1F5F9;font:700 14px/1 sans-serif;';
  inner.textContent = String(pins.length);
  outer.appendChild(inner);
  outer.addEventListener('click', (event) => {
    event.stopPropagation();
    onClick();
  });
  return outer;
}

/** 화면 가득 카카오맵을 그리고 핀을 마커로 보여 준다. 가까운 핀은 묶어서 보여 준다. 키가 없거나 SDK 를 못 불러오면 이유를 안내한다. */
export default function PartyMapView({ ref, pins, selectedPinId, draft, fitKey, panTo, colorMode, heat, onMapClick, onPinClick, onClusterOpen }: Props) {
  const container = useRef<HTMLDivElement>(null);
  const mapRef = useRef<kakao.maps.Map | null>(null);
  const markersRef = useRef(new Map<string, kakao.maps.Marker>());
  const clusterOverlaysRef = useRef<kakao.maps.CustomOverlay[]>([]);
  const draftMarkerRef = useRef<kakao.maps.Marker | null>(null);
  const heatCirclesRef = useRef<kakao.maps.Circle[]>([]);
  const fittedKeyRef = useRef<string | null>(null);
  const handlersRef = useRef({ onMapClick, onPinClick, onClusterOpen });
  /** 이동·확대가 끝날 때마다 불리는 렌더 함수. 최신 props 로 다시 만들어 둔다. */
  const renderRef = useRef<() => void>(() => undefined);
  const [status, setStatus] = useState<Status>(KAKAO_MAP_APP_KEY ? 'loading' : 'no-key');
  const [message, setMessage] = useState('');

  useImperativeHandle(ref, () => ({
    getCenter: () => {
      const center = mapRef.current?.getCenter();
      return center ? { lat: center.getLat(), lng: center.getLng() } : null;
    },
  }), []);

  // 이벤트 핸들러는 마커가 오래 살아 있어도 항상 최신 콜백을 부르도록 ref 로 들고 있는다.
  useEffect(() => {
    handlersRef.current = { onMapClick, onPinClick, onClusterOpen };
  });

  useEffect(() => {
    if (!KAKAO_MAP_APP_KEY) {
      return;
    }
    let cancelled = false;
    const markers = markersRef.current;
    loadKakaoMaps(KAKAO_MAP_APP_KEY)
      .then(() => {
        if (cancelled || !container.current) {
          return;
        }
        const map = new kakao.maps.Map(container.current, {
          center: new kakao.maps.LatLng(DEFAULT_MAP_CENTER.lat, DEFAULT_MAP_CENTER.lng),
          level: DEFAULT_MAP_LEVEL,
        });
        kakao.maps.event.addListener(map, 'click', (event) => {
          handlersRef.current.onMapClick({ lat: event.latLng.getLat(), lng: event.latLng.getLng() });
        });
        // 확대·이동이 끝나면 픽셀 거리가 바뀌므로 묶음을 다시 계산한다
        kakao.maps.event.addListener(map, 'idle', () => renderRef.current());
        mapRef.current = map;
        setStatus('ready');
      })
      .catch((error: unknown) => {
        if (cancelled) {
          return;
        }
        console.error('Kakao map failed to load', error);
        setMessage(error instanceof Error ? error.message : '지도를 불러오지 못했어요.');
        setStatus('error');
      });
    return () => {
      cancelled = true;
      markers.forEach((marker) => marker.setMap(null));
      markers.clear();
      clusterOverlaysRef.current.forEach((overlay) => overlay.setMap(null));
      clusterOverlaysRef.current = [];
      heatCirclesRef.current.forEach((circle) => circle.setMap(null));
      heatCirclesRef.current = [];
      draftMarkerRef.current?.setMap(null);
      draftMarkerRef.current = null;
      mapRef.current = null;
    };
  }, []);

  // 핀 → 마커/묶음. 가까운 핀은 묶음으로, 나머지는 마커로. 선택된 핀은 묶음에 들어가지 않고 따로 보인다.
  useEffect(() => {
    const render = () => {
      const map = mapRef.current;
      if (!map) {
        return;
      }
      const projection = map.getProjection();
      const points = pins.map((pin) => {
        const at = projection.containerPointFromCoords(new kakao.maps.LatLng(pin.lat, pin.lng));
        return { id: pin.id, x: at.x, y: at.y };
      });
      const pointById = new Map(points.map((point) => [point.id, point]));
      const pinById = new Map(pins.map((pin) => [pin.id, pin]));
      const clusters = clusterPoints(points, CLUSTER_PX, selectedPinId ? new Set([selectedPinId]) : undefined);

      const singles = new Set<string>();
      const groups: Pin[][] = [];
      for (const cluster of clusters) {
        if (cluster.ids.length === 1) {
          singles.add(cluster.ids[0]);
        } else {
          groups.push(cluster.ids.flatMap((id) => pinById.get(id) ?? []));
        }
      }

      const markers = markersRef.current;
      for (const id of singles) {
        const pin = pinById.get(id);
        if (!pin) {
          continue;
        }
        const selected = id === selectedPinId;
        const image = toImage(markerIcon(pinColor(pin, colorMode), selected, colorMode === 'author' && pin.status === 'WISH'));
        const position = new kakao.maps.LatLng(pin.lat, pin.lng);
        const existing = markers.get(id);
        if (existing) {
          existing.setPosition(position);
          existing.setImage(image);
          existing.setZIndex(selected ? 2 : 1);
        } else {
          const marker = new kakao.maps.Marker({ position, image, map, title: pin.name, zIndex: selected ? 2 : 1 });
          kakao.maps.event.addListener(marker, 'click', () => handlersRef.current.onPinClick(id));
          markers.set(id, marker);
        }
      }
      markers.forEach((marker, id) => {
        if (!singles.has(id)) {
          marker.setMap(null);
          markers.delete(id);
        }
      });

      clusterOverlaysRef.current.forEach((overlay) => overlay.setMap(null));
      clusterOverlaysRef.current = groups.map((group) => {
        const center = new kakao.maps.LatLng(
          group.reduce((sum, pin) => sum + pin.lat, 0) / group.length,
          group.reduce((sum, pin) => sum + pin.lng, 0) / group.length,
        );
        const onClick = () => {
          const xs = group.map((pin) => pointById.get(pin.id)?.x ?? 0);
          const ys = group.map((pin) => pointById.get(pin.id)?.y ?? 0);
          const extent = Math.max(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys));
          if (extent < SAME_PLACE_PX || map.getLevel() <= 1) {
            // 확대해도 풀리지 않는 같은 장소: 목록으로 보여 준다
            handlersRef.current.onClusterOpen(group);
            return;
          }
          const bounds = new kakao.maps.LatLngBounds();
          group.forEach((pin) => bounds.extend(new kakao.maps.LatLng(pin.lat, pin.lng)));
          map.setBounds(bounds, 90, 60, 90, 60);
        };
        return new kakao.maps.CustomOverlay({
          position: center,
          content: buildClusterElement(group, colorMode, onClick),
          map,
          xAnchor: 0.5,
          yAnchor: 0.5,
          zIndex: 1,
          clickable: true,
        });
      });
    };
    renderRef.current = render;
    if (status === 'ready') {
      render();
    }
  }, [status, pins, selectedPinId, colorMode]);

  // 히트맵: 추천 장소마다 점수에 따라 색·진하기·크기가 다른 원. 원은 지도 클릭을 가로막지 않는다.
  useEffect(() => {
    const map = mapRef.current;
    if (status !== 'ready' || !map) {
      return;
    }
    heatCirclesRef.current.forEach((circle) => circle.setMap(null));
    heatCirclesRef.current = [];
    if (!heat) {
      return;
    }
    heatCirclesRef.current = heat.spots.map((spot) => {
      const style = heatStyle(spot, heat.min, heat.max);
      return new kakao.maps.Circle({
        center: new kakao.maps.LatLng(spot.lat, spot.lng),
        radius: style.radius,
        strokeWeight: 0,
        strokeOpacity: 0,
        fillColor: style.color,
        fillOpacity: style.fillOpacity,
        map,
        zIndex: 0,
      });
    });
  }, [status, heat]);

  // 작성 중인 위치 표시
  useEffect(() => {
    const map = mapRef.current;
    if (status !== 'ready' || !map) {
      return;
    }
    draftMarkerRef.current?.setMap(null);
    draftMarkerRef.current = null;
    if (draft) {
      draftMarkerRef.current = new kakao.maps.Marker({
        position: new kakao.maps.LatLng(draft.lat, draft.lng),
        image: toImage(markerIcon(DRAFT_COLOR, true)),
        map,
        zIndex: 3,
      });
    }
  }, [status, draft]);

  // 지도를 바꿨을 때 핀이 모두 보이도록 한 번만 맞춘다
  useEffect(() => {
    const map = mapRef.current;
    if (status !== 'ready' || !map || fitKey === null || fittedKeyRef.current === fitKey) {
      return;
    }
    fittedKeyRef.current = fitKey;
    if (pins.length === 0) {
      return;
    }
    if (pins.length === 1) {
      map.setCenter(new kakao.maps.LatLng(pins[0].lat, pins[0].lng));
      map.setLevel(SINGLE_PIN_LEVEL);
      return;
    }
    const bounds = new kakao.maps.LatLngBounds();
    pins.forEach((pin) => bounds.extend(new kakao.maps.LatLng(pin.lat, pin.lng)));
    map.setBounds(bounds);
  }, [status, fitKey, pins]);

  useEffect(() => {
    const map = mapRef.current;
    if (status !== 'ready' || !map || !panTo) {
      return;
    }
    const target = new kakao.maps.LatLng(panTo.lat, panTo.lng);
    const element = container.current;
    if (panTo.aboveSheet && element) {
      // 위치를 화면 가운데가 아니라 위(떠 있는 컨트롤)와 아래(시트)에 가려지지 않는 영역의 가운데에 놓으려면, 지도의 중심을 그 위치보다 그만큼 아래로 잡는다
      const projection = map.getProjection();
      const point = projection.containerPointFromCoords(target);
      const height = element.clientHeight;
      const visibleBottom = height * (1 - SHEET_COVER_RATIO);
      const visibleCenter = visibleBottom > FLOATING_CONTROLS_PX ? (FLOATING_CONTROLS_PX + visibleBottom) / 2 : visibleBottom / 2;
      const shift = height / 2 - visibleCenter;
      map.panTo(projection.coordsFromContainerPoint(new kakao.maps.Point(point.x, point.y + shift)));
      return;
    }
    map.panTo(target);
  }, [status, panTo]);

  return (
    <div className="relative h-full w-full bg-slate-800">
      <div ref={container} className="absolute inset-0" aria-label="지도" role="application" />
      {status !== 'ready' && (
        <div className="absolute inset-0 flex items-center justify-center p-8 text-center">
          <p className="max-w-xs text-sm leading-relaxed text-slate-300">
            {status === 'loading' && '지도를 불러오는 중이에요…'}
            {status === 'no-key' && '카카오맵 키가 아직 설정되지 않았어요. 설정이 끝나면 여기에 지도가 보여요.'}
            {status === 'error' && message}
          </p>
        </div>
      )}
    </div>
  );
}
