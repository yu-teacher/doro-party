import { useEffect, useRef, useState } from 'react';
import { loadServices, type LauncherService } from '../utils/servicesManifest';

interface ServiceLauncherProps {
  /** 지금 보고 있는 서비스의 id(services.json 의 id). 목록에서 강조한다. */
  currentId: string;
}

/**
 * 헤더의 "도로 서비스 목록" 버튼. 누르면 도로 홈과 모든 서비스로 가는 링크가 뜬다.
 * 목록은 허브의 /services.json 에서 읽는다(servicesManifest.ts). 이 앱은 목록을 코드에 두지 않는다.
 * 허브·서비스는 이 앱의 라우터 밖이라 일반 링크(<a>)로 이동한다.
 * 목록 창은 가장 가까운 position:relative 조상(헤더 오른쪽 묶음)의 오른쪽 끝에 맞춰 열려서 좁은 폰에서도 화면 안에 들어온다.
 */
export default function ServiceLauncher({ currentId }: ServiceLauncherProps) {
  const [open, setOpen] = useState(false);
  const [services, setServices] = useState<LauncherService[] | null>(null);
  const rootRef = useRef<HTMLDivElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open || services !== null) return;
    let active = true;
    void loadServices().then((list) => {
      if (active) setServices(list);
    });
    return () => {
      active = false;
    };
  }, [open, services]);

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (event: PointerEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) setOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
        buttonRef.current?.focus();
      }
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  const linkClass =
    'flex min-h-11 flex-col justify-center rounded-md px-3 py-1.5 text-slate-200 hover:bg-slate-800 focus-visible:bg-slate-800';

  return (
    <div ref={rootRef}>
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-label="도로 서비스 목록"
        aria-haspopup="true"
        aria-expanded={open}
        title="도로 서비스 목록"
        className="rounded-md p-2 text-slate-400 hover:bg-slate-800 hover:text-slate-100"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <circle cx="5" cy="5" r="2" />
          <circle cx="12" cy="5" r="2" />
          <circle cx="19" cy="5" r="2" />
          <circle cx="5" cy="12" r="2" />
          <circle cx="12" cy="12" r="2" />
          <circle cx="19" cy="12" r="2" />
          <circle cx="5" cy="19" r="2" />
          <circle cx="12" cy="19" r="2" />
          <circle cx="19" cy="19" r="2" />
        </svg>
      </button>
      {open && (
        <nav
          aria-label="도로 서비스 목록"
          className="absolute right-0 top-full z-50 mt-2 w-64 max-w-[calc(100vw-2rem)] rounded-lg border border-slate-700 bg-slate-900 p-1 shadow-xl"
        >
          <ul className="m-0 list-none p-0 text-sm">
            <li>
              <a href="/" className={linkClass}>
                <span className="font-semibold">도로 홈</span>
                <span className="text-xs text-slate-400">모든 서비스 한눈에</span>
              </a>
            </li>
            {(services ?? []).map((service) => (
              <li key={service.id}>
                <a
                  href={service.href}
                  aria-current={service.id === currentId ? 'page' : undefined}
                  className={`${linkClass} ${service.id === currentId ? 'bg-slate-800' : ''}`}
                >
                  <span className="font-semibold">{service.name}</span>
                  {service.desc !== '' && <span className="text-xs text-slate-400">{service.desc}</span>}
                </a>
              </li>
            ))}
          </ul>
          {services === null && <p className="px-3 py-2 text-xs text-slate-400">서비스 목록을 불러오는 중…</p>}
          {services !== null && services.length === 0 && (
            <p className="px-3 py-2 text-xs text-slate-400">서비스 목록을 불러오지 못했어요. 도로 홈에서 모두 볼 수 있어요.</p>
          )}
        </nav>
      )}
    </div>
  );
}
