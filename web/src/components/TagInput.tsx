import { X } from 'lucide-react';
import { useId, useState } from 'react';
import type { KeyboardEvent } from 'react';
import { LIMITS } from '../api/types';
import { addTag } from '../utils/tags';

interface Props {
  tags: string[];
  onChange: (tags: string[]) => void;
}

const REASONS = {
  invalid: `태그는 ${LIMITS.tag}자 이하의 글자, 숫자, '-', '_' 만 쓸 수 있어요.`,
  limit: `태그는 ${LIMITS.tagsPerPin}개까지 붙일 수 있어요.`,
} as const;

/** Enter 나 쉼표로 태그를 더하고, 칩의 x 로 뺀다. 서버와 같은 규칙으로 정리한다. */
export default function TagInput({ tags, onChange }: Props) {
  const inputId = useId();
  const [draft, setDraft] = useState('');
  const [error, setError] = useState<string | null>(null);

  const commit = () => {
    if (draft.trim() === '') {
      setError(null);
      return;
    }
    const result = addTag(tags, draft);
    if (result.ok) {
      onChange(result.tags);
      setDraft('');
      setError(null);
    } else {
      setError(REASONS[result.reason]);
    }
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      commit();
    } else if (event.key === 'Backspace' && draft === '' && tags.length > 0) {
      onChange(tags.slice(0, -1));
    }
  };

  return (
    <div>
      <label htmlFor={inputId} className="mb-1.5 block text-sm font-medium text-slate-300">태그</label>
      <div className="flex flex-wrap items-center gap-1.5 rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-2 focus-within:border-teal-500">
        {tags.map((tag) => (
          <span key={tag} className="flex items-center gap-1 rounded-full bg-slate-800 py-0.5 pl-2.5 pr-1 text-sm text-slate-200">
            #{tag}
            <button type="button" onClick={() => onChange(tags.filter((t) => t !== tag))} className="rounded-full p-0.5 text-slate-400 hover:text-slate-100" aria-label={`${tag} 태그 지우기`}>
              <X size={12} />
            </button>
          </span>
        ))}
        <input
          id={inputId}
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={onKeyDown}
          onBlur={commit}
          placeholder={tags.length === 0 ? '카페, 술집… (Enter 로 추가)' : ''}
          className="min-w-[8rem] flex-1 bg-transparent text-sm text-slate-100 outline-none placeholder:text-slate-500"
        />
      </div>
      {error && <p role="alert" className="mt-1 text-xs text-rose-300">{error}</p>}
    </div>
  );
}
