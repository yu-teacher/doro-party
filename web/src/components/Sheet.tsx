import { X } from 'lucide-react';
import { useEffect, useRef } from 'react';
import type { ReactNode } from 'react';

interface Props {
  title: string;
  onClose: () => void;
  children: ReactNode;
}

/** 지도 위에 올라오는 하단 시트. 지도를 가리지 않도록 배경을 덮지 않고, Esc 로 닫는다. */
export default function Sheet({ title, onClose, children }: Props) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    ref.current?.focus();
  }, []);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose]);

  return (
    <div
      ref={ref}
      role="dialog"
      aria-label={title}
      tabIndex={-1}
      className="absolute inset-x-0 bottom-0 z-20 mx-auto max-h-[65%] max-w-lg overflow-y-auto rounded-t-2xl border-t border-slate-700 bg-slate-900 shadow-2xl outline-none"
    >
      <div className="sticky top-0 flex items-center justify-between bg-slate-900 px-4 pb-2 pt-3">
        <h2 className="text-base font-semibold text-slate-100">{title}</h2>
        <button type="button" onClick={onClose} className="rounded-md p-1.5 text-slate-400 hover:bg-slate-800 hover:text-slate-100" aria-label="닫기">
          <X size={18} />
        </button>
      </div>
      <div className="px-4 pb-5">{children}</div>
    </div>
  );
}
