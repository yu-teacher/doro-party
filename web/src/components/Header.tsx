import { LogIn, LogOut } from 'lucide-react';
import { useLocation } from 'react-router-dom';
import { buildLoginUrl, useAuthStore } from '../store/authStore';

export default function Header() {
  const { user, isAuthenticated, signOut } = useAuthStore();
  const { pathname, search } = useLocation();

  return (
    <header className="flex items-center justify-between border-b border-slate-800 bg-slate-900 px-4 pb-3 pt-[calc(env(safe-area-inset-top)+0.75rem)]">
      <h1 className="text-lg font-bold tracking-tight text-slate-100">도로 파티</h1>
      {isAuthenticated && user ? (
        <div className="flex items-center gap-3">
          <span className="flex items-center gap-2 text-sm text-slate-300">
            <span className="h-3 w-3 rounded-full" style={{ backgroundColor: user.color }} aria-hidden="true" />
            {user.nickname}
          </span>
          <button
            type="button"
            onClick={() => void signOut()}
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
    </header>
  );
}
