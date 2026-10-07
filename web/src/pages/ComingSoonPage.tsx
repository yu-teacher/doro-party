interface Props {
  title: string;
  description: string;
}

/** 아직 만들지 않은 탭의 자리. 마일스톤(M3 친구·공유, M5 모임)에서 실제 화면으로 바뀐다. */
export default function ComingSoonPage({ title, description }: Props) {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-2 p-8 text-center">
      <h2 className="text-lg font-semibold text-slate-100">{title}</h2>
      <p className="max-w-xs text-sm leading-relaxed text-slate-400">{description}</p>
    </div>
  );
}
