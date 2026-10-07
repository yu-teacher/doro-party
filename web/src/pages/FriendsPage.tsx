import { Check, Pencil, UserMinus, UserPlus, X } from 'lucide-react';
import { useCallback, useId, useState } from 'react';
import type { FormEvent } from 'react';
import * as socialApi from '../api/socialApi';
import type { FriendRequestView, FriendView } from '../api/types';
import InviteLinkBox from '../components/InviteLinkBox';
import LoginRequired from '../components/LoginRequired';
import ProfileEditSheet from '../components/ProfileEditSheet';
import Section from '../components/Section';
import UserDot from '../components/UserDot';
import { useResource } from '../hooks/useResource';
import { hasDefaultNickname, useAuthStore } from '../store/authStore';

const CARD = 'rounded-xl bg-slate-800/60 p-3';

export default function FriendsPage() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (!isAuthenticated) {
    return <LoginRequired title="친구" description="로그인하면 친구를 초대하고, 친구와 지도를 나눌 수 있어요." />;
  }
  return <FriendsContent />;
}

function FriendsContent() {
  const user = useAuthStore((state) => state.user);
  const usernameId = useId();
  const overview = useResource(useCallback((signal: AbortSignal) => socialApi.getFriends(signal), []));
  const invite = useResource(useCallback((signal: AbortSignal) => socialApi.getFriendInvite(signal), []));
  const [editingProfile, setEditingProfile] = useState(false);
  const [username, setUsername] = useState('');
  const [requesting, setRequesting] = useState(false);
  const [requestNotice, setRequestNotice] = useState<{ ok: boolean; text: string } | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [confirmingUnfriend, setConfirmingUnfriend] = useState<string | null>(null);

  const friends = overview.data?.friends ?? [];
  const incoming = overview.data?.incoming ?? [];
  const outgoing = overview.data?.outgoing ?? [];

  const act = async (action: () => Promise<void>, failure: string) => {
    setActionError(null);
    try {
      await action();
      overview.reload();
    } catch (error) {
      setActionError(error instanceof Error ? error.message : failure);
    }
  };

  const sendRequest = async (event: FormEvent) => {
    event.preventDefault();
    if (username.trim() === '') {
      return;
    }
    setRequesting(true);
    setRequestNotice(null);
    try {
      const result = await socialApi.requestFriend(username.trim());
      setRequestNotice({ ok: true, text: result.status === 'ACCEPTED' ? `${result.user.nickname}님과 친구가 됐어요.` : `${result.user.nickname}님에게 요청을 보냈어요.` });
      setUsername('');
      overview.reload();
    } catch (error) {
      setRequestNotice({ ok: false, text: error instanceof Error ? error.message : '요청을 보내지 못했어요.' });
    } finally {
      setRequesting(false);
    }
  };

  return (
    <div className="relative h-full">
      <div className="flex h-full flex-col gap-5 overflow-y-auto p-4 pb-8">
        {user && (
          <div className={`${CARD} flex items-center justify-between`}>
            <div className="min-w-0">
              <UserDot nickname={user.nickname} color={user.color} />
              <p className="mt-0.5 text-xs text-slate-500">@{user.username}</p>
            </div>
            <button type="button" onClick={() => setEditingProfile(true)} className="flex items-center gap-1 rounded-md px-2.5 py-1.5 text-xs font-medium text-teal-300 hover:bg-slate-800">
              <Pencil size={13} /> 수정
            </button>
          </div>
        )}
        {hasDefaultNickname(user) && (
          <button type="button" onClick={() => setEditingProfile(true)} className="rounded-xl bg-amber-400/10 px-4 py-3 text-left text-sm text-amber-100 hover:bg-amber-400/15">
            친구에게 보일 <strong>닉네임</strong>을 정해 주세요. 지금은 임시 이름이에요.
          </button>
        )}

        <Section title="친구 초대하기" hint="링크를 열면 바로 친구가 돼요">
          <InviteLinkBox
            kind="friend"
            link={invite.data}
            canManage
            shareTitle="도로 파티"
            shareText={`${user?.nickname ?? '친구'}님이 도로 파티에 초대했어요`}
            onCreate={async () => {
              await socialApi.createFriendInvite();
              invite.reload();
            }}
            onRevoke={async () => {
              await socialApi.revokeFriendInvite();
              invite.reload();
            }}
          />
        </Section>

        <Section title="사용자명으로 요청하기">
          <form onSubmit={(event) => void sendRequest(event)} className="flex gap-2">
            <label htmlFor={usernameId} className="sr-only">친구의 사용자명</label>
            <div className="flex min-w-0 flex-1 items-center rounded-lg border border-slate-700 bg-slate-950 focus-within:border-teal-500">
              <span className="pl-3 text-slate-500">@</span>
              <input id={usernameId} value={username} onChange={(event) => setUsername(event.target.value)} placeholder="사용자명" autoCapitalize="none" autoCorrect="off" spellCheck={false}
                className="min-w-0 flex-1 bg-transparent px-2 py-2.5 text-sm text-slate-100 outline-none placeholder:text-slate-500" />
            </div>
            <button type="submit" disabled={requesting || username.trim() === ''} className="flex items-center gap-1.5 rounded-lg bg-teal-500 px-4 text-sm font-semibold text-slate-950 hover:bg-teal-400 disabled:opacity-50">
              <UserPlus size={15} /> 요청
            </button>
          </form>
          {requestNotice && <p role={requestNotice.ok ? 'status' : 'alert'} className={`text-xs ${requestNotice.ok ? 'text-teal-300' : 'text-rose-300'}`}>{requestNotice.text}</p>}
        </Section>

        {actionError && <p role="alert" className="rounded-lg bg-rose-500/15 px-3 py-2 text-sm text-rose-200">{actionError}</p>}
        {overview.error && <p role="alert" className="rounded-lg bg-rose-500/15 px-3 py-2 text-sm text-rose-200">{overview.error}</p>}

        {incoming.length > 0 && (
          <Section title="받은 요청" hint={`${incoming.length}`}>
            <ul className="flex flex-col gap-2">
              {incoming.map((request: FriendRequestView) => (
                <li key={request.id} className={`${CARD} flex items-center justify-between gap-2`}>
                  <div className="min-w-0"><UserDot nickname={request.user.nickname} color={request.user.color} /><p className="text-xs text-slate-500">@{request.user.username}</p></div>
                  <div className="flex shrink-0 gap-1.5">
                    <button type="button" onClick={() => void act(() => socialApi.acceptFriendRequest(request.id), '수락하지 못했어요.')} className="flex items-center gap-1 rounded-md bg-teal-500 px-3 py-1.5 text-xs font-semibold text-slate-950 hover:bg-teal-400"><Check size={13} /> 수락</button>
                    <button type="button" onClick={() => void act(() => socialApi.removeFriendRequest(request.id), '거절하지 못했어요.')} className="rounded-md border border-slate-600 px-3 py-1.5 text-xs text-slate-300 hover:bg-slate-800">거절</button>
                  </div>
                </li>
              ))}
            </ul>
          </Section>
        )}

        {outgoing.length > 0 && (
          <Section title="보낸 요청" hint="상대가 수락하면 친구가 돼요">
            <ul className="flex flex-col gap-2">
              {outgoing.map((request: FriendRequestView) => (
                <li key={request.id} className={`${CARD} flex items-center justify-between gap-2`}>
                  <div className="min-w-0"><UserDot nickname={request.user.nickname} color={request.user.color} /><p className="text-xs text-slate-500">@{request.user.username}</p></div>
                  <button type="button" onClick={() => void act(() => socialApi.removeFriendRequest(request.id), '취소하지 못했어요.')} className="flex shrink-0 items-center gap-1 rounded-md border border-slate-600 px-3 py-1.5 text-xs text-slate-300 hover:bg-slate-800"><X size={13} /> 취소</button>
                </li>
              ))}
            </ul>
          </Section>
        )}

        <Section title="내 친구" hint={friends.length > 0 ? `${friends.length}명` : undefined}>
          {friends.length === 0 && !overview.loading ? (
            <p className="text-sm text-slate-500">아직 친구가 없어요. 위의 초대 링크를 보내 보세요.</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {friends.map((friend: FriendView) => (
                <li key={friend.user.id} className={CARD}>
                  <div className="flex items-center justify-between gap-2">
                    <div className="min-w-0"><UserDot nickname={friend.user.nickname} color={friend.user.color} /><p className="text-xs text-slate-500">@{friend.user.username}</p></div>
                    {confirmingUnfriend !== friend.user.id && (
                      <button type="button" onClick={() => setConfirmingUnfriend(friend.user.id)} className="flex shrink-0 items-center gap-1 rounded-md px-2.5 py-1.5 text-xs text-slate-400 hover:bg-slate-800 hover:text-rose-300"><UserMinus size={13} /> 끊기</button>
                    )}
                  </div>
                  {confirmingUnfriend === friend.user.id && (
                    <div className="mt-2 rounded-lg bg-rose-500/10 p-2.5">
                      <p className="mb-2 text-xs text-rose-100">{friend.user.nickname}님과 친구를 끊으면, 내가 이 친구에게 공유한 지도가 모두 거둬져요.</p>
                      <div className="flex justify-end gap-2">
                        <button type="button" onClick={() => setConfirmingUnfriend(null)} className="rounded-md px-3 py-1.5 text-xs text-slate-300 hover:bg-slate-800">취소</button>
                        <button type="button" onClick={() => { setConfirmingUnfriend(null); void act(() => socialApi.unfriend(friend.user.id), '친구를 끊지 못했어요.'); }} className="rounded-md bg-rose-500 px-3 py-1.5 text-xs font-semibold text-white hover:bg-rose-400">친구 끊기</button>
                      </div>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </Section>
      </div>
      {editingProfile && <ProfileEditSheet onClose={() => setEditingProfile(false)} />}
    </div>
  );
}
