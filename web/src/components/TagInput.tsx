import { X } from 'lucide-react';
import { useId, useRef, useState } from 'react';
import type { ChangeEvent, KeyboardEvent } from 'react';
import { LIMITS } from '../api/types';
import { addTag, applyTagInput } from '../utils/tags';

interface Props {
  tags: string[];
  onChange: (tags: string[]) => void;
}

const REASONS = {
  invalid: `태그는 ${LIMITS.tag}자 이하의 글자, 숫자, '-', '_' 만 쓸 수 있어요.`,
  limit: `태그는 ${LIMITS.tagsPerPin}개까지 붙일 수 있어요.`,
} as const;

/** 한글 입력기가 조합 중일 때 눌린 키는 keyCode 가 229 다(Safari 는 조합이 끝난 직후의 keydown 도 그렇다). */
const IME_KEY_CODE = 229;

/** Enter 나 쉼표로 태그를 더하고, 칩의 x 로 뺀다. 서버와 같은 규칙으로 정리한다. */
export default function TagInput({ tags, onChange }: Props) {
  const inputId = useId();
  const [draft, setDraft] = useState('');
  const [error, setError] = useState<string | null>(null);
  /** 한글 조합 중에 Enter 가 눌렸다면, 조합이 끝난 뒤(keyup)에 태그로 확정한다. */
  const enterDuringComposition = useRef(false);

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

  const onInput = (event: ChangeEvent<HTMLInputElement>) => {
    const result = applyTagInput(tags, event.target.value);
    if (result.tags !== tags) {
      onChange(result.tags);
    }
    setDraft(result.draft);
    setError(result.error === null ? null : REASONS[result.error]);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') {
      if (event.nativeEvent.isComposing || event.keyCode === IME_KEY_CODE) {
        // 조합 중인 글자를 확정하는 Enter 다. 지금 입력창을 비우면 확정되는 마지막 글자가 다시 들어가므로 건드리지 않는다.
        enterDuringComposition.current = true;
        return;
      }
      event.preventDefault();
      commit();
    } else if (event.key === 'Backspace' && draft === '' && tags.length > 0) {
      onChange(tags.slice(0, -1));
    }
  };

  const onKeyUp = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter' && enterDuringComposition.current) {
      // 조합이 끝나 마지막 글자까지 입력창에 반영된 뒤에 확정한다
      enterDuringComposition.current = false;
      commit();
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
          onChange={onInput}
          onKeyDown={onKeyDown}
          onKeyUp={onKeyUp}
          onBlur={commit}
          placeholder={tags.length === 0 ? '카페, 술집… (Enter 로 추가)' : ''}
          className="min-w-[8rem] flex-1 bg-transparent text-sm text-slate-100 outline-none placeholder:text-slate-500"
        />
      </div>
      {error && <p role="alert" className="mt-1 text-xs text-rose-300">{error}</p>}
    </div>
  );
}
