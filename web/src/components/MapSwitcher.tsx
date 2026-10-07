import { Layers, Plus, Settings } from 'lucide-react';
import { useId } from 'react';
import type { PartyMap } from '../api/types';
import { mapOrigin, ORIGIN_LABEL, ROLE_LABEL } from '../utils/mapRole';
import type { MapOrigin } from '../utils/mapRole';

interface Props {
  maps: PartyMap[];
  selectedMapId: string | null;
  onSelect: (mapId: string) => void;
  onCreate: () => void;
  onSettings: () => void;
  /** 여러 지도를 한 화면에 겹쳐보기 */
  onOverlay: () => void;
}

const ORIGINS: MapOrigin[] = ['mine', 'friend', 'group'];

/** 지도 위쪽에 떠 있는 "어느 지도를 볼까" 선택 바. */
export default function MapSwitcher({ maps, selectedMapId, onSelect, onCreate, onSettings, onOverlay }: Props) {
  const selectId = useId();
  const selected = maps.find((map) => map.id === selectedMapId) ?? null;
  return (
    <div className="flex flex-col gap-1.5 rounded-xl bg-slate-900/95 p-2 shadow-lg ring-1 ring-slate-700 backdrop-blur">
      <div className="flex items-center gap-2">
      <label htmlFor={selectId} className="sr-only">지도 선택</label>
      <select
        id={selectId}
        value={selectedMapId ?? ''}
        onChange={(event) => onSelect(event.target.value)}
        disabled={maps.length === 0}
        className="min-w-0 flex-1 rounded-lg bg-slate-800 px-3 py-2 text-sm font-medium text-slate-100 outline-none disabled:text-slate-500"
      >
        {maps.length === 0 && <option value="">지도가 없어요</option>}
        {ORIGINS.map((origin) => {
          const group = maps.filter((map) => mapOrigin(map) === origin);
          return group.length === 0 ? null : (
            <optgroup key={origin} label={ORIGIN_LABEL[origin]}>
              {group.map((map) => <option key={map.id} value={map.id}>{map.name} ({map.pinCount}){map.role === 'OWNER' && map.friendAccess !== 'NONE' ? ' · 친구 공개' : ''}</option>)}
            </optgroup>
          );
        })}
      </select>
      <button type="button" onClick={onOverlay} disabled={maps.length === 0} className="rounded-lg p-2 text-teal-300 hover:bg-slate-800 disabled:text-slate-600" aria-label="여러 지도 겹쳐보기">
        <Layers size={18} />
      </button>
      <button type="button" onClick={onCreate} className="rounded-lg bg-teal-500 p-2 text-slate-950 hover:bg-teal-400" aria-label="새 지도 만들기">
        <Plus size={18} />
      </button>
      <button type="button" onClick={onSettings} disabled={selectedMapId === null} className="rounded-lg p-2 text-slate-300 hover:bg-slate-800 disabled:text-slate-600" aria-label="지도 설정">
        <Settings size={18} />
      </button>
      </div>
      {selected && selected.role !== 'OWNER' && (
        <p className="truncate px-1 text-xs text-slate-400">
          {selected.ownerNickname}님의 지도 · {ROLE_LABEL[selected.role]}
          {selected.viaGroups.length > 0 && ` · ${selected.viaGroups.join(', ')}`}
        </p>
      )}
    </div>
  );
}
