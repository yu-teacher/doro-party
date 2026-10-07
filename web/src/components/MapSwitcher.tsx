import { Plus, Settings } from 'lucide-react';
import { useId } from 'react';
import type { PartyMap } from '../api/types';

interface Props {
  maps: PartyMap[];
  selectedMapId: string | null;
  onSelect: (mapId: string) => void;
  onCreate: () => void;
  onSettings: () => void;
}

/** 지도 위쪽에 떠 있는 "어느 지도를 볼까" 선택 바. */
export default function MapSwitcher({ maps, selectedMapId, onSelect, onCreate, onSettings }: Props) {
  const selectId = useId();
  return (
    <div className="flex items-center gap-2 rounded-xl bg-slate-900/95 p-2 shadow-lg ring-1 ring-slate-700 backdrop-blur">
      <label htmlFor={selectId} className="sr-only">지도 선택</label>
      <select
        id={selectId}
        value={selectedMapId ?? ''}
        onChange={(event) => onSelect(event.target.value)}
        disabled={maps.length === 0}
        className="min-w-0 flex-1 rounded-lg bg-slate-800 px-3 py-2 text-sm font-medium text-slate-100 outline-none disabled:text-slate-500"
      >
        {maps.length === 0 && <option value="">지도가 없어요</option>}
        {maps.map((map) => (
          <option key={map.id} value={map.id}>{map.name} ({map.pinCount})</option>
        ))}
      </select>
      <button type="button" onClick={onCreate} className="rounded-lg bg-teal-500 p-2 text-slate-950 hover:bg-teal-400" aria-label="새 지도 만들기">
        <Plus size={18} />
      </button>
      <button type="button" onClick={onSettings} disabled={selectedMapId === null} className="rounded-lg p-2 text-slate-300 hover:bg-slate-800 disabled:text-slate-600" aria-label="지도 설정">
        <Settings size={18} />
      </button>
    </div>
  );
}
