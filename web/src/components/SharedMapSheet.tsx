import { useCallback, useState } from 'react';
import * as socialApi from '../api/socialApi';
import type { PartyMap } from '../api/types';
import { useResource } from '../hooks/useResource';
import { useAuthStore } from '../store/authStore';
import { useMapStore } from '../store/mapStore';
import { ROLE_LABEL } from '../utils/mapRole';
import Section from './Section';
import Sheet from './Sheet';
import UserDot from './UserDot';

interface Props {
  map: PartyMap;
  onClose: () => void;
}

const ROLE_NOTE = { OWNER: '주인', EDITOR: '편집자', VIEWER: '열람자' } as const;

/** 남이 공유해 준 지도의 정보: 내 권한, 누가 같이 보는지, 그리고 (직접 공유받은 경우) 나가기. */
export default function SharedMapSheet({ map, onClose }: Props) {
  const me = useAuthStore((state) => state.user);
  const loadMaps = useMapStore((state) => state.loadMaps);
  const members = useResource(useCallback((signal: AbortSignal) => socialApi.listMapMembers(map.id, signal), [map.id]));
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const viaGroupOnly = map.viaGroups.length > 0;

  const leave = async () => {
    if (!me) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await socialApi.revokeShare(map.id, me.id);
      onClose();
      await loadMaps();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '나가지 못했어요.');
      setBusy(false);
    }
  };

  return (
    <Sheet title={map.name} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <UserDot nickname={map.ownerNickname} color={map.ownerColor} note="님의 지도" />
          <p className="text-sm text-slate-300">내 권한: <strong className="text-slate-100">{ROLE_LABEL[map.role]}</strong></p>
          {viaGroupOnly && <p className="text-xs text-slate-500">「{map.viaGroups.join('」, 「')}」 모임 덕분에 볼 수 있어요. 모임에서 나가면 더 볼 수 없어요.</p>}
        </div>

        <Section title="같이 보는 사람" hint={members.data ? `${members.data.length}명` : undefined}>
          <ul className="flex flex-col gap-1.5">
            {(members.data ?? []).map((member) => (
              <li key={member.userId}><UserDot nickname={member.nickname} color={member.color} note={ROLE_NOTE[member.role]} /></li>
            ))}
          </ul>
        </Section>

        {error && <p role="alert" className="rounded-lg bg-rose-500/15 px-3 py-2 text-sm text-rose-200">{error}</p>}

        {!viaGroupOnly && (
          confirming ? (
            <div className="rounded-xl bg-rose-500/10 p-3">
              <p className="mb-2 text-sm text-rose-100">이 지도에서 나갈까요? 목록에서 사라져요. 다시 보려면 {map.ownerNickname}님이 공유해야 해요.</p>
              <div className="flex justify-end gap-2">
                <button type="button" onClick={() => setConfirming(false)} className="rounded-md px-3 py-1.5 text-sm text-slate-300 hover:bg-slate-800">취소</button>
                <button type="button" disabled={busy} onClick={() => void leave()} className="rounded-md bg-rose-500 px-3 py-1.5 text-sm font-semibold text-white hover:bg-rose-400 disabled:opacity-60">나가기</button>
              </div>
            </div>
          ) : (
            <button type="button" onClick={() => setConfirming(true)} className="self-start text-sm text-rose-300 hover:underline">이 지도에서 나가기…</button>
          )
        )}
      </div>
    </Sheet>
  );
}
