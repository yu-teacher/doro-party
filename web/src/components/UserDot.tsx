interface Props {
  nickname: string;
  color: string;
  /** 닉네임 옆에 덧붙이는 작은 설명(역할 등) */
  note?: string;
}

/** 사람을 색 점과 닉네임으로 보여 준다. 겹쳐보기·핀 작성자 표시에서 쓰는 색과 같다. */
export default function UserDot({ nickname, color, note }: Props) {
  return (
    <span className="inline-flex min-w-0 max-w-full items-center gap-2">
      <span className="h-3 w-3 shrink-0 rounded-full" style={{ backgroundColor: color }} aria-hidden="true" />
      <span className="truncate text-sm text-slate-100">{nickname}</span>
      {note && <span className="shrink-0 text-xs text-slate-500">{note}</span>}
    </span>
  );
}
