import { useLocation } from 'react-router-dom';
import { buildLoginUrl } from '../store/authStore';

interface Props {
  title: string;
  description: string;
  /** 로그인 후 돌아올 앱 경로. 없으면 지금 보던 경로. */
  returnPath?: string;
}

/** 로그인해야 쓸 수 있는 화면의 안내. 로그인하면 보던 화면으로 돌아온다. */
export default function LoginRequired({ title, description, returnPath }: Props) {
  const { pathname, search } = useLocation();
  return (
    <div className="flex h-full flex-col items-center justify-center gap-3 p-8 text-center">
      <h2 className="text-lg font-semibold text-slate-100">{title}</h2>
      <p className="max-w-xs text-sm leading-relaxed text-slate-400">{description}</p>
      <a href={buildLoginUrl(returnPath ?? `${pathname}${search}`)} className="rounded-lg bg-teal-500 px-5 py-2.5 text-sm font-semibold text-slate-950 hover:bg-teal-400">
        로그인
      </a>
    </div>
  );
}
