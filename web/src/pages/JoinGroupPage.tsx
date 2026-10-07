import { useCallback, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import * as socialApi from '../api/socialApi';
import LoginRequired from '../components/LoginRequired';
import { useResource } from '../hooks/useResource';
import { useAuthStore } from '../store/authStore';
import { invitePath } from '../utils/inviteLink';

/** 모임 초대 링크(/join/코드)로 들어온 사람에게 어떤 모임인지 보여 주고, 들어가면 멤버가 된다. */
export default function JoinGroupPage() {
  const { code = '' } = useParams();
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (!isAuthenticated) {
    return <LoginRequired title="모임 초대가 도착했어요" description="로그인하면 어떤 모임인지 보고 들어갈 수 있어요." returnPath={invitePath('group', code)} />;
  }
  return <JoinContent code={code} />;
}

function JoinContent({ code }: { code: string }) {
  const navigate = useNavigate();
  const preview = useResource(useCallback((signal: AbortSignal) => socialApi.previewGroupInvite(code, signal), [code]));
  const [joining, setJoining] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const join = async () => {
    setJoining(true);
    setError(null);
    try {
      const result = await socialApi.joinGroup(code);
      navigate(`/groups/${result.groupId}`, { replace: true });
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '모임에 들어가지 못했어요.');
      setJoining(false);
    }
  };

  if (preview.loading && !preview.data) {
    return <p className="p-8 text-center text-sm text-slate-400">초대를 확인하는 중이에요…</p>;
  }
  if (preview.error || !preview.data) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-2 p-8 text-center">
        <h2 className="text-lg font-semibold text-slate-100">쓸 수 없는 초대 링크예요</h2>
        <p className="max-w-xs text-sm text-slate-400">만료됐거나 방장이 다시 만든 링크일 수 있어요. 방장에게 새 링크를 부탁해 보세요.</p>
      </div>
    );
  }

  const { groupName, memberCount, ownerNickname, alreadyMember, full } = preview.data;
  return (
    <div className="flex h-full flex-col items-center justify-center gap-4 p-8 text-center">
      <div className="rounded-2xl bg-slate-800/70 px-6 py-5">
        <p className="mb-1 text-sm text-slate-400">모임 초대</p>
        <p className="text-lg font-bold text-slate-100">{groupName}</p>
        <p className="mt-1 text-xs text-slate-500">방장 {ownerNickname} · 멤버 {memberCount}명</p>
      </div>
      {alreadyMember && <p className="text-sm text-teal-300">이미 이 모임의 멤버예요.</p>}
      {!alreadyMember && full && <p className="text-sm text-rose-300">모임 정원이 가득 찼어요.</p>}
      {error && <p role="alert" className="text-sm text-rose-300">{error}</p>}
      {!alreadyMember && !full && (
        <button type="button" disabled={joining} onClick={() => void join()} className="rounded-lg bg-teal-500 px-6 py-2.5 text-sm font-semibold text-slate-950 hover:bg-teal-400 disabled:opacity-60">
          {joining ? '들어가는 중…' : '모임에 들어가기'}
        </button>
      )}
      {alreadyMember && (
        <button type="button" onClick={() => navigate('/groups')} className="rounded-lg border border-slate-600 px-5 py-2 text-sm text-slate-200 hover:bg-slate-800">내 모임 보기</button>
      )}
    </div>
  );
}
