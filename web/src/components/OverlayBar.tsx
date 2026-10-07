import { Flame, Layers, Sparkles, X } from 'lucide-react';
import type { Author } from '../utils/overlaySelection';

interface Props {
  mapCount: number;
  pinCount: number;
  loading: boolean;
  error: string | null;
  authors: Author[];
  hiddenAuthors: string[];
  onPickMaps: () => void;
  onOpenRecommendations: () => void;
  heatmap: boolean;
  onToggleHeatmap: () => void;
  onToggleAuthor: (userId: string) => void;
  onClose: () => void;
}

/** 겹쳐보기 중에 지도 위쪽에 뜨는 바: 몇 개 지도를 겹쳤는지, 작성자별 범례(눌러서 켜고 끄기), 지도 고르기·닫기. */
export default function OverlayBar({ mapCount, pinCount, loading, error, authors, hiddenAuthors, onPickMaps, onOpenRecommendations, heatmap, onToggleHeatmap, onToggleAuthor, onClose }: Props) {
  return (
    <div className="flex flex-col gap-2 rounded-xl bg-slate-900/95 p-2 shadow-lg ring-1 ring-slate-700 backdrop-blur">
      <div className="flex items-center gap-2">
        <button type="button" onClick={onPickMaps} className="flex min-w-0 flex-1 items-center gap-2 rounded-lg bg-slate-800 px-3 py-2 text-left hover:bg-slate-700">
          <Layers size={16} className="shrink-0 text-teal-300" />
          <span className="min-w-0 truncate text-sm font-medium text-slate-100">
            {loading ? '불러오는 중…' : `겹쳐보기 · 지도 ${mapCount}개 · 핀 ${pinCount}개`}
          </span>
          <span className="shrink-0 text-xs text-teal-300">지도 고르기</span>
        </button>
        <button type="button" onClick={onClose} className="rounded-lg p-2 text-slate-300 hover:bg-slate-800" aria-label="겹쳐보기 닫기">
          <X size={18} />
        </button>
      </div>

      <div className="flex gap-2">
        <button type="button" onClick={onOpenRecommendations} className="flex flex-1 items-center justify-center gap-1.5 rounded-lg bg-teal-500 py-2 text-sm font-semibold text-slate-950 hover:bg-teal-400">
          <Sparkles size={15} /> 오늘 어디 갈까?
        </button>
        <button type="button" role="switch" aria-checked={heatmap} onClick={onToggleHeatmap}
          className={`flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm font-semibold ring-1 ${heatmap ? 'bg-rose-500 text-white ring-rose-500' : 'bg-slate-800 text-slate-200 ring-slate-600 hover:bg-slate-700'}`}>
          <Flame size={15} /> 히트맵
        </button>
      </div>

      {error && <p role="alert" className="px-1 text-xs text-rose-300">{error}</p>}

      {authors.length > 0 && (
        <div className="flex gap-1.5 overflow-x-auto pb-0.5" role="group" aria-label="작성자별 보기">
          {authors.map((author) => {
            const hidden = hiddenAuthors.includes(author.userId);
            return (
              <button
                key={author.userId}
                type="button"
                aria-pressed={!hidden}
                onClick={() => onToggleAuthor(author.userId)}
                className={`flex shrink-0 items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ring-1 ${
                  hidden ? 'bg-slate-900 text-slate-500 ring-slate-700' : 'bg-slate-800 text-slate-100 ring-slate-600'
                }`}
              >
                <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: hidden ? '#475569' : author.color }} aria-hidden="true" />
                <span className={hidden ? 'line-through' : ''}>{author.nickname}</span>
                <span className="text-slate-500">{author.count}</span>
              </button>
            );
          })}
        </div>
      )}
      {authors.length > 0 && <p className="px-1 text-[11px] text-slate-500">색은 핀을 꽂은 사람이에요. 옅은 핀은 가고 싶은 곳, 진한 핀은 다녀온 곳이에요.</p>}
    </div>
  );
}
