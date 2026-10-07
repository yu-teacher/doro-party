import type { PinStatus } from '../api/types';
import type { StatusFilter } from '../store/mapStore';

interface Props {
  statusFilter: StatusFilter;
  tagFilter: string | null;
  tags: string[];
  onStatusChange: (filter: StatusFilter) => void;
  onTagChange: (tag: string | null) => void;
}

const STATUS_CHIPS: ReadonlyArray<{ value: StatusFilter; label: string }> = [
  { value: 'ALL', label: '전체' },
  { value: 'WISH', label: '가고 싶어요' },
  { value: 'VISITED', label: '다녀왔어요' },
];

const ACTIVE: Record<StatusFilter, string> = {
  ALL: 'bg-slate-100 text-slate-900',
  WISH: 'bg-amber-400 text-slate-900',
  VISITED: 'bg-teal-400 text-slate-900',
} satisfies Record<PinStatus | 'ALL', string>;

const CHIP = 'shrink-0 rounded-full px-3 py-1 text-xs font-semibold shadow ring-1 ring-slate-700';

/** 상태와 태그로 핀을 걸러 보는 칩 줄. 가로로 스크롤된다. */
export default function FilterBar({ statusFilter, tagFilter, tags, onStatusChange, onTagChange }: Props) {
  return (
    <div className="flex gap-1.5 overflow-x-auto pb-1" role="toolbar" aria-label="핀 필터">
      {STATUS_CHIPS.map((chip) => (
        <button
          key={chip.value}
          type="button"
          aria-pressed={statusFilter === chip.value}
          onClick={() => onStatusChange(chip.value)}
          className={`${CHIP} ${statusFilter === chip.value ? ACTIVE[chip.value] : 'bg-slate-900/95 text-slate-300'}`}
        >
          {chip.label}
        </button>
      ))}
      {tags.map((tag) => (
        <button
          key={tag}
          type="button"
          aria-pressed={tagFilter === tag}
          onClick={() => onTagChange(tagFilter === tag ? null : tag)}
          className={`${CHIP} ${tagFilter === tag ? 'bg-sky-300 text-slate-900' : 'bg-slate-900/95 text-slate-300'}`}
        >
          #{tag}
        </button>
      ))}
    </div>
  );
}
