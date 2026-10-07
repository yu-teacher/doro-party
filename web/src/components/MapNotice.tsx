import type { ReactNode } from 'react';

/** 지도 가운데 떠서 다음에 할 일을 안내하는 카드. 지도는 그대로 눌러 볼 수 있다. */
export default function MapNotice({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="pointer-events-none absolute inset-x-0 top-1/3 z-10 flex justify-center px-6">
      <div className="pointer-events-auto max-w-xs rounded-2xl bg-slate-900/95 p-5 text-center shadow-xl ring-1 ring-slate-700">
        <h2 className="mb-1 text-base font-semibold text-slate-100">{title}</h2>
        <div className="text-sm leading-relaxed text-slate-300">{children}</div>
      </div>
    </div>
  );
}
