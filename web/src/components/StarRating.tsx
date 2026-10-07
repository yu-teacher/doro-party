import { Star } from 'lucide-react';
import { LIMITS } from '../api/types';

interface Props {
  value: number | null;
  onChange?: (value: number | null) => void;
  size?: number;
}

const STARS = Array.from({ length: LIMITS.ratingMax }, (_, i) => i + 1);

/** 1~5점 별점. 같은 별을 다시 누르면 평점을 지운다. onChange 가 없으면 보기 전용이다. */
export default function StarRating({ value, onChange, size = 22 }: Props) {
  return (
    <div className="flex items-center gap-0.5" role={onChange ? 'group' : 'img'} aria-label={value === null ? '평점 없음' : `평점 ${value}점`}>
      {STARS.map((star) => {
        const filled = value !== null && star <= value;
        const icon = <Star size={size} className={filled ? 'fill-amber-400 text-amber-400' : 'text-slate-600'} aria-hidden="true" />;
        return onChange ? (
          <button
            key={star}
            type="button"
            onClick={() => onChange(value === star ? null : star)}
            aria-label={`${star}점`}
            aria-pressed={value === star}
            className="rounded p-0.5 hover:bg-slate-800"
          >
            {icon}
          </button>
        ) : (
          <span key={star}>{icon}</span>
        );
      })}
    </div>
  );
}
