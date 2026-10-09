import type { Pin } from '../api/types';
import Sheet from './Sheet';
import UserDot from './UserDot';

interface Props {
  pins: Pin[];
  /** 지도 ID -> 지도 이름 */
  mapNames: ReadonlyMap<string, string>;
  onPick: (pin: Pin) => void;
  onClose: () => void;
}

const STATUS_LABEL = { WISH: '가고 싶어요', VISITED: '다녀왔어요' } as const;

/** 같은 장소(확대해도 풀리지 않는 묶음)에 모인 핀들을 목록으로 보여 준다. 누가 어떤 지도에 찍었는지 한눈에 본다. */
export default function ClusterSheet({ pins, mapNames, onPick, onClose }: Props) {
  const authors = new Set(pins.map((pin) => pin.createdBy)).size;
  return (
    <Sheet title={`이 장소의 핀 ${pins.length}개`} onClose={onClose}>
      <p className="mb-3 text-xs text-slate-500">{authors}명이 같은 곳에 핀을 꽂았어요.</p>
      <ul className="flex flex-col gap-2">
        {pins.map((pin) => (
          <li key={pin.id}>
            <button type="button" onClick={() => onPick(pin)} className="flex w-full flex-col gap-1 rounded-xl bg-slate-800/60 p-3 text-left hover:bg-slate-800">
              <span className="flex items-center justify-between gap-2">
                <span className="truncate text-sm font-semibold text-slate-100">{pin.name}</span>
                <span className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold ${pin.status === 'WISH' ? 'bg-amber-400/15 text-amber-200' : 'bg-teal-400/15 text-teal-200'}`}>{STATUS_LABEL[pin.status]}</span>
              </span>
              <span className="flex items-center gap-2 text-xs text-slate-500">
                <UserDot nickname={pin.authorNickname} color={pin.authorColor} />
                <span className="truncate">· {mapNames.get(pin.mapId) ?? '지도'}</span>
                {pin.rating !== null && <span className="shrink-0 text-amber-300">★ {pin.rating}</span>}
                {pin.commentCount > 0 && <span className="shrink-0" aria-label={`댓글 ${pin.commentCount}개`}>💬 {pin.commentCount}</span>}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </Sheet>
  );
}
