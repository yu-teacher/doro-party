import { Bell, BellRing, House, LogIn, LogOut } from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';
import { detachThisDevice } from '../push/pushClient';
import { buildLoginUrl, useAuthStore } from '../store/authStore';
import { usePushStore } from '../store/pushStore';
import ServiceLauncher from './ServiceLauncher';

export default function Header() {
  const { user, isAuthenticated, signOut } = useAuthStore();
  const { pathname, search } = useLocation();
  const pushOn = usePushStore((state) => state.status === 'on');

  /** 로그아웃 전에 이 기기를 내 계정의 알림 기기에서 뗀다(실패해도 로그아웃은 한다). */
  const logOut = async () => {
    try {
      await detachThisDevice();
    } catch (failure) {
      console.warn('Failed to detach push device before sign out', failure);
    }
    await signOut();
  };

  return (
    <header className="flex items-center justify-between border-b border-slate-800 bg-slate-900 px-4 pb-3 pt-[calc(env(safe-area-inset-top)+0.75rem)]">
      <div className="flex items-center gap-1">
        {/* 허브(/)는 이 앱의 라우터 밖이라 일반 링크로 이동한다 */}
        <a
          href="/"
          className="-ml-2 rounded-md p-2 text-slate-400 hover:bg-slate-800 hover:text-slate-100"
          aria-label="도로 홈으로"
          title="도로 홈으로"
        >
          <House size={18} />
        </a>
        <h1 className="text-lg font-bold tracking-tight text-slate-100">
          <Link to="/" className="inline-block py-1">
            도로 파티
          </Link>
        </h1>
      </div>
      <div className="relative flex items-center gap-2">
      <ServiceLauncher currentId="party" />
      {isAuthenticated && user ? (
        <div className="flex items-center gap-3">
          <span className="flex items-center gap-2 text-sm text-slate-300">
            <span className="h-3 w-3 rounded-full" style={{ backgroundColor: user.color }} aria-hidden="true" />
            {user.nickname}
          </span>
          <button
            type="button"
            onClick={() => usePushStore.getState().openSettings()}
            className="rounded-md p-2 text-slate-400 hover:bg-slate-800 hover:text-slate-100"
            aria-label="알림 설정"
          >
            {pushOn ? <BellRing size={18} className="text-teal-300" /> : <Bell size={18} />}
          </button>
          <button
            type="button"
            onClick={() => void logOut()}
            className="rounded-md p-2 text-slate-400 hover:bg-slate-800 hover:text-slate-100"
            aria-label="로그아웃"
          >
            <LogOut size={18} />
          </button>
        </div>
      ) : (
        <a
          href={buildLoginUrl(`${pathname}${search}`)}
          className="flex items-center gap-1.5 rounded-md bg-teal-500 px-3 py-1.5 text-sm font-semibold text-slate-950 hover:bg-teal-400"
        >
          <LogIn size={16} />
          로그인
        </a>
      )}
      </div>
    </header>
  );
}
