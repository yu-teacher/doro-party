import { ArrowLeft, Crown, Layers, MapPin, UserMinus } from 'lucide-react';
import { useCallback, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import * as mapsApi from '../api/mapsApi';
import * as socialApi from '../api/socialApi';
import type { GroupMember } from '../api/types';
import GroupNameSheet from '../components/GroupNameSheet';
import InviteLinkBox from '../components/InviteLinkBox';
import LoginRequired from '../components/LoginRequired';
import Section from '../components/Section';
import UserDot from '../components/UserDot';
import { useResource } from '../hooks/useResource';
import { useAuthStore } from '../store/authStore';
import { useOverlayStore } from '../store/overlayStore';
import { MAX_OVERLAY_MAPS } from '../utils/overlaySelection';
import { writeSelectedMapId } from '../utils/selectedMapStorage';

const CARD = 'rounded-xl bg-slate-800/60 p-3';
const SECONDARY = 'rounded-md px-2.5 py-1.5 text-xs text-slate-300 hover:bg-slate-800';

type Pending = { kind: 'kick'; member: GroupMember } | { kind: 'transfer'; member: GroupMember } | { kind: 'leave' } | { kind: 'delete' } | null;

export default function GroupPage() {
  const { groupId = '' } = useParams();
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (!isAuthenticated) {
    return <LoginRequired title="모임" description="로그인하면 모임을 볼 수 있어요." />;
  }
  return <GroupContent groupId={groupId} />;
}

function GroupContent({ groupId }: { groupId: string }) {
  const navigate = useNavigate();
  const me = useAuthStore((state) => state.user);
  const detail = useResource(useCallback((signal: AbortSignal) => socialApi.getGroup(groupId, signal), [groupId]));
  const invite = useResource(useCallback((signal: AbortSignal) => socialApi.getGroupInvite(groupId, signal), [groupId]));
  const sharedMaps = useResource(useCallback((signal: AbortSignal) => socialApi.listGroupMaps(groupId, signal), [groupId]));
  const myMaps = useResource(useCallback((signal: AbortSignal) => mapsApi.listMyMaps(signal), []));
  const friends = useResource(useCallback((signal: AbortSignal) => socialApi.getFriends(signal), []));
  const [renaming, setRenaming] = useState(false);
  const [pending, setPending] = useState<Pending>(null);
  const [friendToInvite, setFriendToInvite] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const group = detail.data;
  const isOwner = group?.myRole === 'OWNER';
  const memberIds = useMemo(() => new Set(group?.members.map((member) => member.userId) ?? []), [group]);
  const invitableFriends = (friends.data?.friends ?? []).filter((friend) => !memberIds.has(friend.user.id));
  const sharedIds = useMemo(() => new Set((sharedMaps.data ?? []).map((map) => map.id)), [sharedMaps.data]);
  const mine = (myMaps.data ?? []).filter((map) => map.role === 'OWNER');

  const run = async (action: () => Promise<void>, failure: string, after?: () => void) => {
    setBusy(true);
    setError(null);
    try {
      await action();
      after?.();
    } catch (e) {
      setError(e instanceof Error ? e.message : failure);
    } finally {
      setBusy(false);
    }
  };

  const refresh = () => {
    detail.reload();
    sharedMaps.reload();
    myMaps.reload();
  };

  /** 이 모임에 공유된 지도를 모두 한 지도에 겹쳐서 보여 준다. */
  const overlayGroupMaps = () => {
    const ids = (sharedMaps.data ?? []).map((map) => map.id).slice(0, MAX_OVERLAY_MAPS);
    void useOverlayStore.getState().open(ids);
    navigate('/');
  };

  const openMap = (mapId: string) => {
    writeSelectedMapId(mapId);
    navigate('/');
  };

  if (detail.loading && !group) {
    return <p className="p-8 text-center text-sm text-slate-400">모임을 불러오는 중이에요…</p>;
  }
  if (!group) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-3 p-8 text-center">
        <h2 className="text-lg font-semibold text-slate-100">모임을 볼 수 없어요</h2>
        <p className="max-w-xs text-sm text-slate-400">모임이 사라졌거나 멤버가 아니에요.</p>
        <button type="button" onClick={() => navigate('/groups')} className="rounded-lg border border-slate-600 px-5 py-2 text-sm text-slate-200 hover:bg-slate-800">내 모임으로</button>
      </div>
    );
  }

  const confirmCopy = (() => {
    switch (pending?.kind) {
      case 'kick': return `${pending.member.nickname}님을 내보낼까요? 이 분이 모임에 공유한 지도도 거둬져요.`;
      case 'transfer': return `${pending.member.nickname}님에게 방장을 넘길까요? 나는 일반 멤버가 돼요.`;
      case 'leave': return '모임을 나갈까요? 내가 이 모임에 공유한 지도도 거둬지고, 이 모임의 지도는 더 볼 수 없어요.';
      case 'delete': return '모임을 지울까요? 멤버와 공유 기록이 모두 사라져요. 지도는 각자의 것으로 남아요.';
      default: return '';
    }
  })();

  const confirmPending = () => {
    const current = pending;
    setPending(null);
    if (!current) {
      return;
    }
    if (current.kind === 'kick') {
      void run(() => socialApi.kickMember(groupId, current.member.userId), '내보내지 못했어요.', refresh);
    } else if (current.kind === 'transfer') {
      void run(() => socialApi.transferGroupOwner(groupId, current.member.userId), '방장을 넘기지 못했어요.', refresh);
    } else if (current.kind === 'leave') {
      void run(() => socialApi.leaveGroup(groupId), '나가지 못했어요.', () => navigate('/groups'));
    } else {
      void run(() => socialApi.deleteGroup(groupId), '지우지 못했어요.', () => navigate('/groups'));
    }
  };

  return (
    <div className="relative h-full">
      <div className="flex h-full flex-col gap-5 overflow-y-auto p-4 pb-8">
        <div className="flex items-center gap-2">
          <button type="button" onClick={() => navigate('/groups')} className="rounded-full p-1.5 text-slate-400 hover:bg-slate-800 hover:text-slate-100" aria-label="모임 목록으로"><ArrowLeft size={20} /></button>
          <h2 className="min-w-0 flex-1 truncate text-lg font-bold text-slate-100">{group.name}</h2>
          {isOwner && <button type="button" onClick={() => setRenaming(true)} className={SECONDARY}>이름 변경</button>}
        </div>

        {error && <p role="alert" className="rounded-lg bg-rose-500/15 px-3 py-2 text-sm text-rose-200">{error}</p>}
        {pending && (
          <div className="rounded-xl bg-rose-500/10 p-3">
            <p className="mb-2 text-sm text-rose-100">{confirmCopy}</p>
            <div className="flex justify-end gap-2">
              <button type="button" onClick={() => setPending(null)} className={SECONDARY}>취소</button>
              <button type="button" disabled={busy} onClick={confirmPending} className="rounded-md bg-rose-500 px-3 py-1.5 text-xs font-semibold text-white hover:bg-rose-400 disabled:opacity-60">확인</button>
            </div>
          </div>
        )}

        <Section title="모임에 공유된 지도" hint={`${sharedMaps.data?.length ?? 0}개`}>
          {(sharedMaps.data ?? []).length === 0 ? (
            <p className="text-sm text-slate-500">아직 공유된 지도가 없어요. 아래에서 내 지도를 공유해 보세요.</p>
          ) : (
            <>
            <button type="button" onClick={overlayGroupMaps} className="flex items-center justify-center gap-2 rounded-xl bg-teal-500 py-3 text-sm font-semibold text-slate-950 hover:bg-teal-400">
              <Layers size={16} /> 이 모임의 지도 한 화면에 겹쳐보기
            </button>
            <ul className="flex flex-col gap-2">
              {(sharedMaps.data ?? []).map((map) => (
                <li key={map.id} className={`${CARD} flex items-center justify-between gap-2`}>
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-slate-100">{map.name}</p>
                    <div className="mt-0.5 flex items-center gap-2 text-xs text-slate-500"><UserDot nickname={map.ownerNickname} color={map.ownerColor} /> · 핀 {map.pinCount}개</div>
                  </div>
                  <button type="button" onClick={() => openMap(map.id)} className="flex shrink-0 items-center gap-1 rounded-md bg-teal-500 px-3 py-1.5 text-xs font-semibold text-slate-950 hover:bg-teal-400"><MapPin size={13} /> 보기</button>
                </li>
              ))}
            </ul>
            </>
          )}
        </Section>

        <Section title="내 지도를 이 모임에 공유" hint="공유하면 멤버 모두가 볼 수 있어요(보기만)">
          {mine.length === 0 ? (
            <p className="text-sm text-slate-500">공유할 내 지도가 없어요. 지도 탭에서 먼저 지도를 만들어 보세요.</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {mine.map((map) => {
                const shared = sharedIds.has(map.id);
                return (
                  <li key={map.id} className={`${CARD} flex items-center justify-between gap-2`}>
                    <p className="min-w-0 truncate text-sm text-slate-100">{map.name}</p>
                    <button
                      type="button"
                      disabled={busy}
                      role="switch"
                      aria-checked={shared}
                      aria-label={`${map.name} 공유`}
                      onClick={() => void run(() => (shared ? socialApi.unshareMapFromGroup(map.id, groupId) : socialApi.shareMapWithGroup(map.id, groupId).then(() => undefined)), '공유를 바꾸지 못했어요.', refresh)}
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

        <Section title="멤버" hint={`${group.members.length}명`}>
          <ul className="flex flex-col gap-2">
            {group.members.map((member) => {
              const isMe = member.userId === me?.id;
              return (
                <li key={member.userId} className={`${CARD} flex items-center justify-between gap-2`}>
                  <span className="flex min-w-0 items-center gap-2">
                    <UserDot nickname={member.nickname} color={member.color} note={isMe ? '나' : undefined} />
                    {member.role === 'OWNER' && <span className="flex shrink-0 items-center gap-0.5 rounded-full bg-amber-400/15 px-2 py-0.5 text-[10px] font-semibold text-amber-200"><Crown size={10} /> 방장</span>}
                  </span>
                  {isOwner && !isMe && (
                    <span className="flex shrink-0 gap-1">
                      <button type="button" onClick={() => setPending({ kind: 'transfer', member })} className={SECONDARY}>방장 넘기기</button>
                      <button type="button" onClick={() => setPending({ kind: 'kick', member })} className="flex items-center gap-1 rounded-md px-2.5 py-1.5 text-xs text-slate-400 hover:bg-slate-800 hover:text-rose-300"><UserMinus size={12} /> 내보내기</button>
                    </span>
                  )}
                </li>
              );
            })}
          </ul>
        </Section>

        <Section title="친구 초대하기">
          <InviteLinkBox
            kind="group"
            link={invite.data}
            canManage={isOwner}
            shareTitle={group.name}
            shareText={`${me?.nickname ?? '친구'}님이 「${group.name}」 모임에 초대했어요`}
            onCreate={async () => {
              await socialApi.createGroupInvite(groupId);
              invite.reload();
            }}
            onRevoke={async () => {
              await socialApi.revokeGroupInvite(groupId);
              invite.reload();
            }}
          />
          {invitableFriends.length > 0 && (
            <div className="flex gap-2">
              <label htmlFor="invite-friend" className="sr-only">초대할 친구</label>
              <select id="invite-friend" value={friendToInvite} onChange={(event) => setFriendToInvite(event.target.value)} className="min-w-0 flex-1 rounded-lg bg-slate-800 px-3 py-2 text-sm text-slate-100 outline-none">
                <option value="">내 친구에게 바로 초대…</option>
                {invitableFriends.map((friend) => <option key={friend.user.id} value={friend.user.id}>{friend.user.nickname}</option>)}
              </select>
              <button type="button" disabled={busy || friendToInvite === ''} onClick={() => void run(() => socialApi.inviteFriendToGroup(groupId, friendToInvite), '초대하지 못했어요.', () => { setFriendToInvite(''); refresh(); })}
                className="rounded-lg bg-slate-700 px-4 text-sm font-semibold text-slate-100 hover:bg-slate-600 disabled:opacity-40">초대</button>
            </div>
          )}
        </Section>

        <div className="border-t border-slate-800 pt-4">
          {isOwner ? (
            <div className="flex flex-col items-start gap-2">
              <button type="button" onClick={() => setPending({ kind: 'delete' })} className="text-sm text-rose-300 hover:underline">모임 삭제…</button>
              <p className="text-xs text-slate-500">방장은 나갈 수 없어요. 다른 멤버에게 방장을 넘기면 나갈 수 있어요.</p>
            </div>
          ) : (
            <button type="button" onClick={() => setPending({ kind: 'leave' })} className="text-sm text-rose-300 hover:underline">모임 나가기…</button>
          )}
        </div>
      </div>

      {renaming && (
        <GroupNameSheet
          title="모임 이름 변경"
          initialName={group.name}
          submitLabel="저장"
          onSubmit={async (name) => {
            await socialApi.renameGroup(groupId, name);
            detail.reload();
            setRenaming(false);
          }}
          onClose={() => setRenaming(false)}
        />
      )}
    </div>
  );
}
