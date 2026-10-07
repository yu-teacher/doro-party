import { Trash2 } from 'lucide-react';
import { useCallback, useState } from 'react';
import * as mapsApi from '../api/mapsApi';
import * as socialApi from '../api/socialApi';
import type { FriendAccess, PartyMap, ShareRole } from '../api/types';
import { useResource } from '../hooks/useResource';
import { useMapStore } from '../store/mapStore';
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

const ACCESS_OPTIONS: ReadonlyArray<{ value: FriendAccess; label: string; hint: string }> = [
  { value: 'NONE', label: '비공개', hint: '아래에서 고른 친구·모임만 볼 수 있어요.' },
  { value: 'VIEWER', label: '친구 전체 보기', hint: '지금 친구와 앞으로 생길 친구 모두가 볼 수 있어요. 친구의 “친구 지도 둘러보기”에 나타나요. 친구를 끊으면 자동으로 보이지 않아요.' },
  { value: 'EDITOR', label: '친구 전체 편집', hint: '친구 모두가 이 지도에 핀을 꽂고 고칠 수 있어요. 지도 삭제와 이름 변경은 나만 할 수 있어요.' },
];

/**
 * 내 지도의 공개 범위: 친구 전체에게 한꺼번에 공개(보기/편집)하거나, 특정 친구(보기/편집)·모임(보기)에게만 공유한다.
 * 지도 주인만 열 수 있다.
 */
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

  const changeAccess = (access: FriendAccess) => {
    if (access === map.friendAccess) {
      return;
    }
    void run(
      async () => {
        const updated = await mapsApi.setFriendAccess(map.id, access);
        useMapStore.getState().applyMap(updated);
      },
      '공개 범위를 바꾸지 못했어요.',
      () => undefined,
    );
  };
  const currentAccess = ACCESS_OPTIONS.find((option) => option.value === map.friendAccess) ?? ACCESS_OPTIONS[0];

  return (
    <Sheet title={`「${map.name}」 공개 범위`} onClose={onClose}>
      <div className="flex flex-col gap-5">
        {error && <p role="alert" className="rounded-lg bg-rose-500/15 px-3 py-2 text-sm text-rose-200">{error}</p>}

        <Section title="친구 전체에게 공개" hint="한 번에 모든 친구에게 적용돼요">
          <div role="radiogroup" aria-label="친구 전체 공개 범위" className="grid grid-cols-3 gap-1.5">
            {ACCESS_OPTIONS.map((option) => (
              <button
                key={option.value}
                type="button"
                role="radio"
                aria-checked={map.friendAccess === option.value}
                disabled={busy}
                onClick={() => changeAccess(option.value)}
                className={`rounded-lg px-2 py-2 text-xs font-semibold ring-1 transition-colors disabled:opacity-60 ${
                  map.friendAccess === option.value
                    ? option.value === 'EDITOR' ? 'bg-amber-400 text-slate-950 ring-amber-300' : 'bg-teal-400 text-slate-950 ring-teal-300'
                    : 'bg-slate-800 text-slate-300 ring-slate-700 hover:bg-slate-700'
                }`}
              >
                {option.label}
              </button>
            ))}
          </div>
          <p className={`text-xs ${map.friendAccess === 'EDITOR' ? 'text-amber-300' : 'text-slate-400'}`}>{currentAccess.hint}</p>
        </Section>

        <Section title="특정 친구에게 공유" hint="한 명씩 고르고 권한을 정해요(친구에게만 공유할 수 있어요)">
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
