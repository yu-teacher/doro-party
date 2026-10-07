import { Trash2 } from 'lucide-react';
import { useCallback, useState } from 'react';
import * as socialApi from '../api/socialApi';
import type { PartyMap, ShareRole } from '../api/types';
import { useResource } from '../hooks/useResource';
import Section from './Section';
import Sheet from './Sheet';
import UserDot from './UserDot';

interface Props {
  map: PartyMap;
  onClose: () => void;
}

const ROLE_OPTIONS: ReadonlyArray<{ value: ShareRole; label: string }> = [
  { value: 'VIEWER', label: '보기만' },
  { value: 'EDITOR', label: '핀도 꽂기' },
];

/** 내 지도를 친구(보기/편집)나 모임(보기)에 공유하고 거둔다. 지도 주인만 열 수 있다. */
export default function MapShareSheet({ map, onClose }: Props) {
  const shares = useResource(useCallback((signal: AbortSignal) => socialApi.listShares(map.id, signal), [map.id]));
  const friends = useResource(useCallback((signal: AbortSignal) => socialApi.getFriends(signal), []));
  const groups = useResource(useCallback((signal: AbortSignal) => socialApi.listGroups(signal), []));
  const sharedGroups = useResource(useCallback((signal: AbortSignal) => socialApi.listGroupsOfMap(map.id, signal), [map.id]));
  const [friendId, setFriendId] = useState('');
  const [role, setRole] = useState<ShareRole>('VIEWER');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const sharedUserIds = new Set((shares.data ?? []).map((share) => share.user.id));
  const candidates = (friends.data?.friends ?? []).filter((friend) => !sharedUserIds.has(friend.user.id));
  const sharedGroupIds = new Set((sharedGroups.data ?? []).map((group) => group.groupId));

  const run = async (action: () => Promise<unknown>, failure: string, after: () => void) => {
    setBusy(true);
    setError(null);
    try {
      await action();
      after();
    } catch (e) {
      setError(e instanceof Error ? e.message : failure);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Sheet title={`「${map.name}」 공유`} onClose={onClose}>
      <div className="flex flex-col gap-5">
        {error && <p role="alert" className="rounded-lg bg-rose-500/15 px-3 py-2 text-sm text-rose-200">{error}</p>}

        <Section title="친구에게 공유" hint="친구에게만 공유할 수 있어요">
          {(shares.data ?? []).length > 0 && (
            <ul className="flex flex-col gap-2">
              {(shares.data ?? []).map((share) => (
                <li key={share.user.id} className="flex items-center justify-between gap-2 rounded-xl bg-slate-800/60 p-3">
                  <UserDot nickname={share.user.nickname} color={share.user.color} />
                  <span className="flex shrink-0 items-center gap-1.5">
                    <select
                      aria-label={`${share.user.nickname}님의 권한`}
                      value={share.role}
                      disabled={busy}
                      onChange={(event) => void run(() => socialApi.shareMap(map.id, share.user.id, event.target.value as ShareRole), '권한을 바꾸지 못했어요.', shares.reload)}
                      className="rounded-md bg-slate-700 px-2 py-1.5 text-xs text-slate-100 outline-none"
                    >
                      {ROLE_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
                    </select>
                    <button type="button" disabled={busy} onClick={() => void run(() => socialApi.revokeShare(map.id, share.user.id), '공유를 끊지 못했어요.', shares.reload)}
                      className="rounded p-1.5 text-slate-500 hover:bg-slate-800 hover:text-rose-300" aria-label={`${share.user.nickname}님과 공유 끊기`}><Trash2 size={14} /></button>
                  </span>
                </li>
              ))}
            </ul>
          )}
          {candidates.length > 0 ? (
            <div className="flex gap-2">
              <label htmlFor="share-friend" className="sr-only">공유할 친구</label>
              <select id="share-friend" value={friendId} onChange={(event) => setFriendId(event.target.value)} className="min-w-0 flex-1 rounded-lg bg-slate-800 px-3 py-2 text-sm text-slate-100 outline-none">
                <option value="">친구 고르기…</option>
                {candidates.map((friend) => <option key={friend.user.id} value={friend.user.id}>{friend.user.nickname}</option>)}
              </select>
              <label htmlFor="share-role" className="sr-only">권한</label>
              <select id="share-role" value={role} onChange={(event) => setRole(event.target.value as ShareRole)} className="rounded-lg bg-slate-800 px-2 text-sm text-slate-100 outline-none">
                {ROLE_OPTIONS.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
              </select>
              <button type="button" disabled={busy || friendId === ''} onClick={() => void run(() => socialApi.shareMap(map.id, friendId, role), '공유하지 못했어요.', () => { setFriendId(''); shares.reload(); })}
                className="rounded-lg bg-teal-500 px-4 text-sm font-semibold text-slate-950 hover:bg-teal-400 disabled:opacity-40">공유</button>
            </div>
          ) : (
            <p className="text-xs text-slate-500">{(friends.data?.friends ?? []).length === 0 ? '친구 탭에서 먼저 친구를 초대해 보세요.' : '모든 친구에게 이미 공유했어요.'}</p>
          )}
        </Section>

        <Section title="모임에 공유" hint="모임 멤버 모두가 볼 수 있어요(보기만)">
          {(groups.data ?? []).length === 0 ? (
            <p className="text-xs text-slate-500">속한 모임이 없어요. 모임 탭에서 만들거나 초대를 받아 보세요.</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {(groups.data ?? []).map((group) => {
                const shared = sharedGroupIds.has(group.id);
                return (
                  <li key={group.id} className="flex items-center justify-between gap-2 rounded-xl bg-slate-800/60 p-3">
                    <span className="min-w-0 truncate text-sm text-slate-100">{group.name} <span className="text-xs text-slate-500">· {group.memberCount}명</span></span>
                    <button
                      type="button"
                      disabled={busy}
                      role="switch"
                      aria-checked={shared}
                      aria-label={`${group.name}에 공유`}
                      onClick={() => void run(() => (shared ? socialApi.unshareMapFromGroup(map.id, group.id) : socialApi.shareMapWithGroup(map.id, group.id)), '공유를 바꾸지 못했어요.', sharedGroups.reload)}
                      className={`relative h-6 w-11 shrink-0 rounded-full transition-colors ${shared ? 'bg-teal-500' : 'bg-slate-600'} disabled:opacity-60`}
                    >
                      <span className={`absolute top-0.5 h-5 w-5 rounded-full bg-white transition-all ${shared ? 'left-[22px]' : 'left-0.5'}`} />
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </Section>
      </div>
    </Sheet>
  );
}
