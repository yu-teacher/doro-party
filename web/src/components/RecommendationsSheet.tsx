import type { RecommendedPlace, ScoreBreakdown } from '../api/types';
import Sheet from './Sheet';

interface Props {
  places: RecommendedPlace[];
  loading: boolean;
  error: string | null;
  minPeople: number;
  /** 지금 추천에서 빼 둔 사람 수(범례에서 감춘 작성자) */
  excludedCount: number;
  heatmap: boolean;
  onMinPeople: (minPeople: number) => void;
  onToggleHeatmap: () => void;
  onPick: (place: RecommendedPlace) => void;
  onClose: () => void;
}

const MIN_PEOPLE_OPTIONS: ReadonlyArray<{ value: number; label: string }> = [
  { value: 1, label: '전체' },
  { value: 2, label: '2명 이상' },
  { value: 3, label: '3명 이상' },
];

const sign = (points: number) => (points > 0 ? `+${points}` : String(points));

/** 점수 내역을 사람이 읽는 조각으로: 0점 항목은 빼고, 어떤 항목이 몇 점인지 보여 준다. */
export function breakdownParts(b: ScoreBreakdown): string[] {
  const parts = [`${b.people}명이 찍었어요 ${sign(b.peoplePoints)}`];
  if (b.wishCount > 0) parts.push(`가고 싶어요 ${b.wishCount}명 ${sign(b.wishPoints)}`);
  if (b.visitedCount > 0) parts.push(`다녀왔어요 ${b.visitedCount}명 ${sign(b.visitedPoints)}`);
  if (b.againCount > 0) parts.push(`또 가고 싶어요 ${b.againCount}명 ${sign(b.againPoints)}`);
  if (b.onceCount > 0) parts.push(`한 번이면 충분 ${b.onceCount}명 ${sign(b.oncePoints)}`);
  if (b.ratedCount > 0 && b.ratingAverage !== null && b.ratingPoints !== 0) parts.push(`평점 평균 ${b.ratingAverage} ${sign(b.ratingPoints)}`);
  return parts;
}

/** "오늘 어디 갈까": 여러 명이 찍은 장소를 점수 순으로 보여 주고, 점수가 어떻게 나왔는지 내역을 함께 보여 준다. */
export default function RecommendationsSheet({ places, loading, error, minPeople, excludedCount, heatmap, onMinPeople, onToggleHeatmap, onPick, onClose }: Props) {
  return (
    <Sheet title="오늘 어디 갈까?" onClose={onClose}>
      <div className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center gap-1.5">
          {MIN_PEOPLE_OPTIONS.map((option) => (
            <button
              key={option.value}
              type="button"
              aria-pressed={minPeople === option.value}
              onClick={() => onMinPeople(option.value)}
              className={`rounded-full px-3 py-1 text-xs font-semibold ring-1 ${minPeople === option.value ? 'bg-slate-100 text-slate-900 ring-slate-100' : 'bg-slate-800 text-slate-300 ring-slate-600'}`}
            >
              {option.label}
            </button>
          ))}
          <button type="button" role="switch" aria-checked={heatmap} onClick={onToggleHeatmap}
            className={`ml-auto rounded-full px-3 py-1 text-xs font-semibold ring-1 ${heatmap ? 'bg-rose-500 text-white ring-rose-500' : 'bg-slate-800 text-slate-300 ring-slate-600'}`}>
            히트맵 {heatmap ? '끄기' : '보기'}
          </button>
        </div>
        {excludedCount > 0 && <p className="text-xs text-amber-200">{excludedCount}명을 빼고 계산한 결과예요. 위쪽 사람 칩을 다시 누르면 포함돼요.</p>}
        {error && <p role="alert" className="rounded-lg bg-rose-500/15 px-3 py-2 text-sm text-rose-200">{error}</p>}
        {loading && places.length === 0 && <p className="text-sm text-slate-400">계산하는 중이에요…</p>}
        {!loading && !error && places.length === 0 && (
          <p className="rounded-xl bg-slate-800/50 p-4 text-center text-sm leading-relaxed text-slate-400">
            {minPeople > 1 ? `${minPeople}명 이상이 같은 곳을 찍은 장소가 아직 없어요. '전체'로 바꿔 보세요.` : '추천할 장소가 아직 없어요. 지도에 핀이 있는지 확인해 보세요.'}
          </p>
        )}
        <ol className="flex flex-col gap-2">
          {places.map((place) => (
            <li key={`${place.rank}-${place.pinIds[0]}`}>
              <button type="button" onClick={() => onPick(place)} className="flex w-full flex-col gap-2 rounded-xl bg-slate-800/60 p-3 text-left hover:bg-slate-800">
                <span className="flex items-center gap-3">
                  <span className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-sm font-bold ${place.rank === 1 ? 'bg-amber-400 text-slate-900' : 'bg-slate-700 text-slate-200'}`}>{place.rank}</span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-semibold text-slate-100">{place.name}</span>
                    {place.otherNames.length > 0 && <span className="block truncate text-xs text-slate-500">{place.otherNames.join(' · ')}</span>}
                  </span>
                  <span className="shrink-0 text-right">
                    <span className="block text-lg font-bold leading-none text-teal-300">{place.score}</span>
                    <span className="text-[10px] text-slate-500">점</span>
                  </span>
                </span>
                <span className="flex items-center gap-1.5">
                  {place.authors.map((author) => (
                    <span key={author.userId} title={author.nickname} className="h-3.5 w-3.5 rounded-full ring-1 ring-slate-900" style={{ backgroundColor: author.color }} aria-label={author.nickname} />
                  ))}
                  <span className="ml-1 text-xs text-slate-400">{place.authors.map((author) => author.nickname).join(', ')}</span>
                </span>
                <span className="flex flex-wrap gap-1">
                  {breakdownParts(place.breakdown).map((part) => (
                    <span key={part} className="rounded-full bg-slate-900/70 px-2 py-0.5 text-[11px] text-slate-300">{part}</span>
                  ))}
                </span>
              </button>
            </li>
          ))}
        </ol>
      </div>
    </Sheet>
  );
}
