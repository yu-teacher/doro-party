import { useCallback, useMemo, useState } from 'react';
import * as socialApi from '../api/socialApi';
import type { PartyMap } from '../api/types';
import { useResource } from '../hooks/useResource';
import { mapOrigin, ORIGIN_LABEL } from '../utils/mapRole';
import type { MapOrigin } from '../utils/mapRole';
import { MAX_OVERLAY_MAPS, selectOnly, toggleAll } from '../utils/overlaySelection';
import type { SelectionChange } from '../utils/overlaySelection';
import Sheet from './Sheet';
import UserDot from './UserDot';

interface Props {
  /** 내가 볼 수 있는 모든 지도 */
  maps: PartyMap[];
  /** 지금 겹쳐 보고 있는 지도 */
  selectedIds: string[];
  onApply: (mapIds: string[]) => void;
  onClose: () => void;
}

const ORIGINS: MapOrigin[] = ['mine', 'friend', 'group'];

/** 겹쳐볼 지도를 고른다. 내 지도, 친구가 공유한 지도, 모임에 공유된 지도를 한꺼번에 고를 수 있고, 모임 이름을 누르면 그 모임의 지도만 고른다. */
export default function OverlayMapsSheet({ maps, selectedIds, onApply, onClose }: Props) {
  const groups = useResource(useCallback((signal: AbortSignal) => socialApi.listGroups(signal), []));
  const available = useMemo(() => new Set(maps.map((map) => map.id)), [maps]);
  const [selected, setSelected] = useState<Set<string>>(() => new Set(selectedIds.filter((id) => available.has(id))));
  const [error, setError] = useState<string | null>(null);

  const toggle = (mapId: string) => {
    setError(null);
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(mapId)) {
        next.delete(mapId);
      } else if (next.size >= MAX_OVERLAY_MAPS) {
        setError(`한 번에 겹쳐볼 수 있는 지도는 최대 ${MAX_OVERLAY_MAPS}개예요.`);
      } else {
        next.add(mapId);
      }
      return next;
    });
  };

  /** 일괄 선택 결과를 반영하고, 상한 때문에 다 고르지 못했다면 알린다. */
  const apply = (change: SelectionChange) => {
    setSelected(change.selected);
    setError(change.truncated ? `한 번에 겹쳐볼 수 있는 지도는 최대 ${MAX_OVERLAY_MAPS}개라서 그만큼만 골랐어요.` : null);
  };

  const idsOf = (origins: MapOrigin[]) => maps.filter((map) => origins.includes(mapOrigin(map))).map((map) => map.id);

  const selectGroup = async (groupId: string) => {
    setError(null);
    try {
      const groupMaps = await socialApi.listGroupMaps(groupId);
      apply(selectOnly(groupMaps.map((map) => map.id).filter((id) => available.has(id))));
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '모임의 지도를 불러오지 못했어요.');
    }
  };

  return (
    <Sheet title="겹쳐볼 지도 고르기" onClose={onClose}>
      <div className="flex flex-col gap-4">
        <div>
          <p className="mb-1.5 text-xs text-slate-500">한 번에 고르기</p>
          <div className="flex flex-wrap gap-1.5">
            <button type="button" onClick={() => apply(selectOnly(idsOf(['mine'])))} className="rounded-full bg-teal-500/15 px-3 py-1 text-xs font-semibold text-teal-200 ring-1 ring-teal-500/40 hover:bg-teal-500/25">내 지도 모두</button>
            <button type="button" onClick={() => apply(selectOnly(idsOf(['friend', 'group'])))} className="rounded-full bg-teal-500/15 px-3 py-1 text-xs font-semibold text-teal-200 ring-1 ring-teal-500/40 hover:bg-teal-500/25">공유된 지도 모두</button>
            {(groups.data ?? []).map((group) => (
              <button key={group.id} type="button" onClick={() => void selectGroup(group.id)} className="rounded-full bg-slate-800 px-3 py-1 text-xs font-medium text-slate-200 ring-1 ring-slate-600 hover:bg-slate-700">
                {group.name}
              </button>
            ))}
            <button type="button" onClick={() => apply(selectOnly(maps.map((map) => map.id)))} className="rounded-full px-3 py-1 text-xs text-slate-400 hover:bg-slate-800">전체</button>
            <button type="button" onClick={() => setSelected(new Set())} className="rounded-full px-3 py-1 text-xs text-slate-400 hover:bg-slate-800">해제</button>
          </div>
        </div>

        {ORIGINS.map((origin) => {
          const list = maps.filter((map) => mapOrigin(map) === origin);
          return list.length === 0 ? null : (
            <section key={origin} aria-label={ORIGIN_LABEL[origin]}>
              <div className="mb-1.5 flex items-center justify-between">
                <h3 className="text-xs font-semibold text-slate-400">{ORIGIN_LABEL[origin]} <span className="font-normal text-slate-500">({list.length})</span></h3>
                <button type="button" onClick={() => apply(toggleAll(selected, list.map((map) => map.id)))} className="rounded px-2 py-0.5 text-xs text-teal-300 hover:bg-slate-800">
                  {list.every((map) => selected.has(map.id)) ? '모두 해제' : '모두 선택'}
                </button>
              </div>
              <ul className="flex flex-col gap-1.5">
                {list.map((map) => (
                  <li key={map.id}>
                    <label className="flex cursor-pointer items-center gap-3 rounded-xl bg-slate-800/60 p-3 hover:bg-slate-800">
                      <input type="checkbox" checked={selected.has(map.id)} onChange={() => toggle(map.id)} className="h-4 w-4 accent-teal-500" />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-medium text-slate-100">{map.name}</span>
                        <span className="mt-0.5 flex items-center gap-2 text-xs text-slate-500">
                          {origin !== 'mine' && <UserDot nickname={map.ownerNickname} color={map.ownerColor} />}
                          핀 {map.pinCount}개{map.viaGroups.length > 0 && ` · ${map.viaGroups.join(', ')}`}
                        </span>
                      </span>
                    </label>
                  </li>
                ))}
              </ul>
            </section>
          );
        })}

        {error && <p role="alert" className="rounded-lg bg-rose-500/15 px-3 py-2 text-sm text-rose-200">{error}</p>}

        <div className="flex gap-2">
          <button type="button" onClick={onClose} className="flex-1 rounded-lg border border-slate-700 py-2.5 text-sm font-medium text-slate-300 hover:bg-slate-800">취소</button>
          <button type="button" disabled={selected.size === 0} onClick={() => onApply([...selected])} className="flex-[2] rounded-lg bg-teal-500 py-2.5 text-sm font-semibold text-slate-950 hover:bg-teal-400 disabled:opacity-50">
            {selected.size === 0 ? '지도를 골라 주세요' : `지도 ${selected.size}개 겹쳐보기`}
          </button>
        </div>
      </div>
    </Sheet>
  );
}
