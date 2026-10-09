import { RefreshCw } from 'lucide-react';
import { useCallback, useMemo, useState } from 'react';
import { getPinsOfMaps } from '../api/socialApi';
import type { PartyMap, Pin } from '../api/types';
import { useCurrentPosition } from '../hooks/useCurrentPosition';
import { useResource } from '../hooks/useResource';
import type { StatusFilter } from '../store/mapStore';
import { formatDistance, nearbyPins, DEFAULT_RADIUS_METERS, RADIUS_OPTIONS } from '../utils/nearby';
import { MAX_OVERLAY_MAPS } from '../utils/overlaySelection';
import { collectTags } from '../utils/tags';
import Sheet from './Sheet';
import UserDot from './UserDot';

interface Props {
  /** 내가 볼 수 있는 지도 전부(내 지도 + 공유받은 지도) */
  maps: ReadonlyArray<PartyMap>;
  onPick: (pin: Pin) => void;
  onClose: () => void;
}

const STATUS_CHIPS: ReadonlyArray<{ value: StatusFilter; label: string }> = [
  { value: 'ALL', label: '전체' },
  { value: 'WISH', label: '가고 싶어요' },
  { value: 'VISITED', label: '다녀왔어요' },
];

const STATUS_LABEL = { WISH: '가고 싶어요', VISITED: '다녀왔어요' } as const;
/** 한 번에 그리는 항목 수(반경을 "전체"로 두면 수천 개가 될 수 있어 끊어서 보여 준다) */
const PAGE_SIZE = 50;
const CHIP = 'shrink-0 rounded-full px-3 py-1 text-xs font-semibold ring-1 ring-slate-700';
const CHIP_OFF = 'bg-slate-800 text-slate-300 hover:bg-slate-700';

/**
 * 내 위치에서 가까운 순으로 핀을 보여 주는 시트. 내 위치는 이 시트를 열 때 한 번만 확인하고(다시 확인 버튼으로 갱신),
 * 거리 계산은 폰 안에서만 한다(위치를 서버로 보내지 않는다). 내가 볼 수 있는 모든 지도의 핀이 대상이다.
 */
export default function NearbySheet({ maps, onPick, onClose }: Props) {
  const { state: position, retry } = useCurrentPosition();
  const [radius, setRadius] = useState<number | null>(DEFAULT_RADIUS_METERS);
  const [status, setStatus] = useState<StatusFilter>('ALL');
  const [tag, setTag] = useState<string | null>(null);
  const [shown, setShown] = useState(PAGE_SIZE);

  const idsKey = maps.map((map) => map.id).join(',');
  const loadPins = useCallback((signal: AbortSignal) => getPinsOfMaps(idsKey === '' ? [] : idsKey.split(','), MAX_OVERLAY_MAPS, signal), [idsKey]);
  const pinsResource = useResource(loadPins);
  const pins = pinsResource.data;

  const mapNames = useMemo(() => new Map(maps.map((map) => [map.id, map.name])), [maps]);
  const tags = useMemo(() => collectTags(pins ?? []), [pins]);
  const activeTag = tag !== null && tags.includes(tag) ? tag : null;

  const results = useMemo(
    () => (position.status === 'ready' && pins !== null ? nearbyPins(pins, position.position, { radiusMeters: radius, status, tag: activeTag }) : []),
    [position, pins, radius, status, activeTag],
  );
  const nearestOutside = useMemo(
    () => (position.status === 'ready' && pins !== null && results.length === 0 ? nearbyPins(pins, position.position, { radiusMeters: null, status, tag: activeTag })[0] ?? null : null),
    [position, pins, results.length, status, activeTag],
  );

  // 조건을 바꾸면 목록을 처음부터 다시 보여 준다
  const pickRadius = (value: number | null) => {
    setRadius(value);
    setShown(PAGE_SIZE);
  };
  const pickStatus = (value: StatusFilter) => {
    setStatus(value);
    setShown(PAGE_SIZE);
  };
  const pickTag = (value: string | null) => {
    setTag(value);
    setShown(PAGE_SIZE);
  };

  const body = () => {
    if (position.status === 'locating') {
      return <p className="py-6 text-center text-sm text-slate-400">내 위치를 확인하고 있어요…</p>;
    }
    if (position.status === 'error') {
      return (
        <div className="flex flex-col items-center gap-3 py-6 text-center">
          <p className="text-sm text-amber-300">{position.message}</p>
          <button type="button" onClick={retry} className="rounded-lg bg-slate-800 px-3 py-1.5 text-xs font-semibold text-slate-100 hover:bg-slate-700">다시 시도</button>
        </div>
      );
    }
    if (pinsResource.loading && pins === null) {
      return <p className="py-6 text-center text-sm text-slate-400">지도의 핀을 불러오고 있어요…</p>;
    }
    if (pinsResource.error !== null && pins === null) {
      return (
        <div className="flex flex-col items-center gap-3 py-6 text-center">
          <p className="text-sm text-amber-300">{pinsResource.error}</p>
          <button type="button" onClick={pinsResource.reload} className="rounded-lg bg-slate-800 px-3 py-1.5 text-xs font-semibold text-slate-100 hover:bg-slate-700">다시 시도</button>
        </div>
      );
    }
    if (pins !== null && pins.length === 0) {
      return <p className="py-6 text-center text-sm text-slate-400">아직 핀이 없어요. 지도에서 가고 싶은 곳을 꽂아 보세요.</p>;
    }
    if (results.length === 0) {
      return (
        <div className="py-6 text-center text-sm text-slate-400">
          <p>조건에 맞는 핀이 없어요.</p>
          {nearestOutside !== null && (
            <p className="mt-1 text-xs text-slate-500">
              가장 가까운 핀은 {formatDistance(nearestOutside.meters)} 떨어진 “{nearestOutside.pin.name}” 이에요. 반경을 넓혀 보세요.
            </p>
          )}
        </div>
      );
    }
    return (
      <>
        <ul className="flex flex-col gap-2">
          {results.slice(0, shown).map(({ pin, meters }) => (
            <li key={pin.id}>
              <button type="button" onClick={() => onPick(pin)} className="flex w-full flex-col gap-1 rounded-xl bg-slate-800/60 p-3 text-left hover:bg-slate-800">
                <span className="flex items-center justify-between gap-2">
                  <span className="truncate text-sm font-semibold text-slate-100">{pin.name}</span>
                  <span className="shrink-0 text-sm font-bold text-teal-300">{formatDistance(meters)}</span>
                </span>
                <span className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-slate-500">
                  <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${pin.status === 'WISH' ? 'bg-amber-400/15 text-amber-200' : 'bg-teal-400/15 text-teal-200'}`}>{STATUS_LABEL[pin.status]}</span>
                  <UserDot nickname={pin.authorNickname} color={pin.authorColor} />
                  <span className="truncate">· {mapNames.get(pin.mapId) ?? '지도'}</span>
                  {pin.rating !== null && <span className="shrink-0 text-amber-300">★ {pin.rating}</span>}
                  {pin.commentCount > 0 && <span className="shrink-0" aria-label={`댓글 ${pin.commentCount}개`}>💬 {pin.commentCount}</span>}
                </span>
                {pin.tags.length > 0 && <span className="truncate text-xs text-sky-300/80">{pin.tags.map((value) => `#${value}`).join(' ')}</span>}
              </button>
            </li>
          ))}
        </ul>
        {results.length > shown && (
          <button type="button" onClick={() => setShown((current) => current + PAGE_SIZE)} className="mt-2 w-full rounded-lg bg-slate-800 py-2 text-xs font-semibold text-slate-200 hover:bg-slate-700">
            더 보기 ({results.length - shown}개 남음)
          </button>
        )}
      </>
    );
  };

  return (
    <Sheet title="내 주변 핀" onClose={onClose}>
      <div className="mb-3 flex flex-col gap-2">
        <div className="flex gap-1.5 overflow-x-auto" role="toolbar" aria-label="반경">
          {RADIUS_OPTIONS.map((option) => (
            <button
              key={option.label}
              type="button"
              aria-pressed={radius === option.meters}
              onClick={() => pickRadius(option.meters)}
              className={`${CHIP} ${radius === option.meters ? 'bg-teal-400 text-slate-900' : CHIP_OFF}`}
            >
              {option.label}
            </button>
          ))}
          <button type="button" onClick={retry} aria-label="내 위치 다시 확인" className={`${CHIP} ml-auto flex items-center gap-1 ${CHIP_OFF}`}>
            <RefreshCw size={12} /> 위치
          </button>
        </div>
        <div className="flex gap-1.5 overflow-x-auto" role="toolbar" aria-label="핀 필터">
          {STATUS_CHIPS.map((chip) => (
            <button
              key={chip.value}
              type="button"
              aria-pressed={status === chip.value}
              onClick={() => pickStatus(chip.value)}
              className={`${CHIP} ${status === chip.value ? 'bg-slate-100 text-slate-900' : CHIP_OFF}`}
            >
              {chip.label}
            </button>
          ))}
          {tags.map((value) => (
            <button
              key={value}
              type="button"
              aria-pressed={activeTag === value}
              onClick={() => pickTag(activeTag === value ? null : value)}
              className={`${CHIP} ${activeTag === value ? 'bg-sky-300 text-slate-900' : CHIP_OFF}`}
            >
              #{value}
            </button>
          ))}
        </div>
        {position.status === 'ready' && results.length > 0 && <p className="text-xs text-slate-500">가까운 순 · {results.length}개</p>}
      </div>
      {body()}
    </Sheet>
  );
}
