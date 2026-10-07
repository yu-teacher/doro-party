import { useEffect, useRef, useState } from 'react';
import type { Pin } from '../api/types';
import { DEFAULT_MAP_CENTER, DEFAULT_MAP_LEVEL, KAKAO_MAP_APP_KEY } from '../config';
import { loadKakaoMaps } from './loadKakaoMaps';
import { DRAFT_COLOR, markerIcon, PIN_COLORS } from './markerIcon';
import type { MarkerIconSpec } from './markerIcon';

type Status = 'loading' | 'ready' | 'error' | 'no-key';

export interface LatLngLiteral {
  lat: number;
  lng: number;
}

interface Props {
  pins: Pin[];
  selectedPinId: string | null;
  /** 아직 저장하지 않은, 방금 눌러 놓은 위치 */
  draft: LatLngLiteral | null;
  /** 이 값이 바뀌면(그리고 null 이 아니면) 핀이 모두 보이도록 지도를 맞춘다. 핀이 불러와진 뒤의 지도 ID 를 넘긴다. */
  fitKey: string | null;
  /** nonce 가 바뀔 때마다 그 위치로 지도를 옮긴다(내 위치 버튼 등). */
  panTo: (LatLngLiteral & { nonce: number }) | null;
  onMapClick: (position: LatLngLiteral) => void;
  onPinClick: (pinId: string) => void;
}

const SINGLE_PIN_LEVEL = 3;

function toImage(icon: MarkerIconSpec): kakao.maps.MarkerImage {
  return new kakao.maps.MarkerImage(icon.url, new kakao.maps.Size(icon.width, icon.height), {
    offset: new kakao.maps.Point(icon.width / 2, icon.height),
  });
}

/** 화면 가득 카카오맵을 그리고 핀을 마커로 보여 준다. 키가 없거나 SDK 를 못 불러오면 이유를 안내한다. */
export default function PartyMapView({ pins, selectedPinId, draft, fitKey, panTo, onMapClick, onPinClick }: Props) {
  const container = useRef<HTMLDivElement>(null);
  const mapRef = useRef<kakao.maps.Map | null>(null);
  const markersRef = useRef(new Map<string, kakao.maps.Marker>());
  const draftMarkerRef = useRef<kakao.maps.Marker | null>(null);
  const fittedKeyRef = useRef<string | null>(null);
  const handlersRef = useRef({ onMapClick, onPinClick });
  const [status, setStatus] = useState<Status>(KAKAO_MAP_APP_KEY ? 'loading' : 'no-key');
  const [message, setMessage] = useState('');

  // 이벤트 핸들러는 마커가 오래 살아 있어도 항상 최신 콜백을 부르도록 ref 로 들고 있는다.
  useEffect(() => {
    handlersRef.current = { onMapClick, onPinClick };
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
      draftMarkerRef.current?.setMap(null);
      draftMarkerRef.current = null;
      mapRef.current = null;
    };
  }, []);

  // 핀 → 마커 동기화(추가·이동·삭제만 반영하고 나머지는 그대로 둔다)
  useEffect(() => {
    const map = mapRef.current;
    if (status !== 'ready' || !map) {
      return;
    }
    const markers = markersRef.current;
    const seen = new Set<string>();
    for (const pin of pins) {
      seen.add(pin.id);
      const selected = pin.id === selectedPinId;
      const position = new kakao.maps.LatLng(pin.lat, pin.lng);
      const image = toImage(markerIcon(PIN_COLORS[pin.status], selected));
      const existing = markers.get(pin.id);
      if (existing) {
        existing.setPosition(position);
        existing.setImage(image);
        existing.setZIndex(selected ? 2 : 1);
      } else {
        const marker = new kakao.maps.Marker({ position, image, map, title: pin.name, zIndex: selected ? 2 : 1 });
        kakao.maps.event.addListener(marker, 'click', () => handlersRef.current.onPinClick(pin.id));
        markers.set(pin.id, marker);
      }
    }
    markers.forEach((marker, id) => {
      if (!seen.has(id)) {
        marker.setMap(null);
        markers.delete(id);
      }
    });
  }, [status, pins, selectedPinId]);

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
    if (status === 'ready' && map && panTo) {
      map.panTo(new kakao.maps.LatLng(panTo.lat, panTo.lng));
    }
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
