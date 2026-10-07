import type { ReactNode } from 'react';

interface Props {
  title: string;
  /** 제목 옆의 작은 보조 설명(개수 등) */
  hint?: string;
  children: ReactNode;
}

export default function Section({ title, hint, children }: Props) {
  return (
    <section className="flex flex-col gap-2">
      <h2 className="flex items-baseline gap-2 text-sm font-semibold text-slate-300">
        {title}
        {hint && <span className="text-xs font-normal text-slate-500">{hint}</span>}
      </h2>
      {children}
    </section>
  );
}
