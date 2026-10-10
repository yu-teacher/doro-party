import { useCallback, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import * as socialApi from '../api/socialApi';
import LoginRequired from '../components/LoginRequired';
import UserDot from '../components/UserDot';
import { useResource } from '../hooks/useResource';
import { useAuthStore } from '../store/authStore';
import { invitePath } from '../utils/inviteLink';

/** 친구 초대 링크(/invite/코드)로 들어온 사람에게 누구의 초대인지 보여 주고, 수락하면 바로 친구가 된다. */
export default function InvitePage() {
  const { code = '' } = useParams();
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (!isAuthenticated) {
    return <LoginRequired title="친구 초대가 도착했어요" description="로그인하면 누가 초대했는지 보고 친구가 될 수 있어요." returnPath={invitePath('friend', code)} />;
  }
  return <InviteContent code={code} />;
}

function InviteContent({ code }: { code: string }) {
  const navigate = useNavigate();
  const preview = useResource(useCallback((signal: AbortSignal) => socialApi.previewFriendInvite(code, signal), [code]));
  const [accepting, setAccepting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const accept = async () => {
    setAccepting(true);
    setError(null);
    try {
      await socialApi.acceptFriendInvite(code);
      navigate('/friends', { replace: true });
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '친구가 되지 못했어요.');
      setAccepting(false);
    }
  };

  if (preview.loading && !preview.data) {
    return <p className="p-8 text-center text-sm text-slate-400">초대를 확인하는 중이에요…</p>;
  }
  if (preview.error || !preview.data) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-2 p-8 text-center">
        <h2 className="text-lg font-semibold text-slate-100">쓸 수 없는 초대 링크예요</h2>
        <p className="max-w-xs text-sm text-slate-400">만료됐거나 다시 만들어진 링크일 수 있어요. 초대한 친구에게 새 링크를 부탁해 보세요.</p>
      </div>
    );
  }

  const { inviter, self, alreadyFriends } = preview.data;
  return (
    <div className="flex h-full flex-col items-center justify-center gap-4 p-8 text-center">
      <div className="max-w-full rounded-2xl bg-slate-800/70 px-6 py-5">
        <p className="mb-2 text-sm text-slate-400">친구 초대</p>
        <UserDot nickname={inviter.nickname} color={inviter.color} />
      </div>
      {self && <p className="text-sm text-slate-400">내가 만든 초대 링크예요. 친구에게 보내 주세요.</p>}
      {!self && alreadyFriends && <p className="text-sm text-teal-300">이미 친구예요.</p>}
      {error && <p role="alert" className="text-sm text-rose-300">{error}</p>}
      {!self && !alreadyFriends && (
        <button type="button" disabled={accepting} onClick={() => void accept()} className="max-w-full break-all rounded-lg bg-teal-500 px-6 py-2.5 text-sm font-semibold text-slate-950 hover:bg-teal-400 disabled:opacity-60">
          {accepting ? '연결하는 중…' : `${inviter.nickname}님과 친구 되기`}
        </button>
      )}
      <button type="button" onClick={() => navigate('/friends')} className="text-sm text-slate-400 hover:text-slate-200">내 친구 보기</button>
    </div>
  );
}
