import { ArrowLeft, MapPin } from 'lucide-react';
import { useCallback, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import * as socialApi from '../api/socialApi';
import type { PartyMap } from '../api/types';
import LoginRequired from '../components/LoginRequired';
import UserDot from '../components/UserDot';
import { useResource } from '../hooks/useResource';
import { useAuthStore } from '../store/authStore';
import { useMapStore } from '../store/mapStore';
import { useOverlayStore } from '../store/overlayStore';

const CARD = 'rounded-xl bg-slate-800/60 p-3';

export default function FriendMapsPage() {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  if (!isAuthenticated) {
    return <LoginRequired title="친구 지도" description="로그인하면 친구들이 공개한 지도를 둘러볼 수 있어요." />;
  }
  return <FriendMapsContent />;
}

/** 친구가 "친구 전체에게 공개" 해 둔 지도를 친구별로 둘러본다. 고르면 그 지도를 열어 내 지도 화면에서 본다. */
function FriendMapsContent() {
  const navigate = useNavigate();
  const overview = useResource(useCallback((signal: AbortSignal) => socialApi.getFriendMaps(signal), []));
  const [openError, setOpenError] = useState<string | null>(null);
  const friends = overview.data?.friends ?? [];

  const open = async (map: PartyMap) => {
    setOpenError(null);
    try {
      useOverlayStore.getState().close();
      await useMapStore.getState().openMap(map);
      navigate('/');
    } catch (error) {
      setOpenError(error instanceof Error ? error.message : '지도를 열지 못했어요.');
    }
  };

  return (
    <div className="flex h-full flex-col gap-4 overflow-y-auto p-4 pb-8">
      <div className="flex items-center gap-2">
        <Link to="/friends" aria-label="친구로 돌아가기" className="rounded-md p-1.5 text-slate-400 hover:bg-slate-800 hover:text-slate-100">
          <ArrowLeft size={18} />
        </Link>
        <h1 className="text-base font-semibold text-slate-100">친구 지도 둘러보기</h1>
      </div>
      <p className="text-xs text-slate-500">친구들이 “친구 전체에게 공개”한 지도예요. 내 지도 화면에서 열어 볼 수 있어요.</p>

      {openError && <p role="alert" className="rounded-lg bg-rose-500/15 px-3 py-2 text-sm text-rose-200">{openError}</p>}
      {overview.error && (
        <div role="alert" className="flex items-center justify-between gap-2 rounded-lg bg-rose-500/15 px-3 py-2 text-sm text-rose-200">
          <span>{overview.error}</span>
          <button type="button" onClick={overview.reload} className="shrink-0 rounded-md bg-slate-800 px-2.5 py-1 text-xs font-semibold text-slate-100 hover:bg-slate-700">다시 시도</button>
        </div>
      )}
      {overview.loading && overview.data === null && <p className="py-6 text-center text-sm text-slate-400">불러오는 중…</p>}
      {!overview.loading && overview.error === null && friends.length === 0 && (
        <p className="py-6 text-center text-sm text-slate-400">
          아직 공개된 지도가 없어요. 친구가 지도의 설정에서 “친구 전체에게 공개”를 켜면 여기에 나타나요.
        </p>
      )}

      {friends.map(({ friend, maps }) => (
        <section key={friend.id} aria-label={`${friend.nickname}님의 공개 지도`} className="flex flex-col gap-2">
          <h2 className="flex items-baseline gap-2 text-sm font-semibold text-slate-300">
            <UserDot nickname={friend.nickname} color={friend.color} />
            <span className="text-xs font-normal text-slate-500">지도 {maps.length}개</span>
          </h2>
          <ul className="flex flex-col gap-2">
            {maps.map((map) => (
              <li key={map.id}>
                <button type="button" onClick={() => void open(map)} className={`${CARD} flex w-full flex-col gap-1 text-left hover:bg-slate-800`}>
                  <span className="flex items-center justify-between gap-2">
                    <span className="truncate text-sm font-semibold text-slate-100">{map.name}</span>
                    <span className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-semibold ${map.role === 'EDITOR' ? 'bg-amber-400/15 text-amber-200' : 'bg-teal-400/15 text-teal-200'}`}>
                      {map.role === 'EDITOR' ? '핀도 꽂기 가능' : '보기만'}
                    </span>
                  </span>
                  {map.description && <span className="truncate text-xs text-slate-400">{map.description}</span>}
                  <span className="flex items-center gap-1 text-xs text-slate-500"><MapPin size={12} aria-hidden="true" /> 핀 {map.pinCount}개</span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}
