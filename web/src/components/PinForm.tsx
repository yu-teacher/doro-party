import { useId, useState } from 'react';
import type { FormEvent } from 'react';
import { LIMITS } from '../api/types';
import type { PinStatus } from '../api/types';
import { validatePinForm } from '../utils/pinForm';
import type { PinFormValues } from '../utils/pinForm';
import StarRating from './StarRating';
import TagInput from './TagInput';

interface Props {
  initial: PinFormValues;
  submitLabel: string;
  /** 저장을 시도한다. 실패하면 예외를 던지고, 이 폼이 그 메시지를 보여 준다. */
  onSubmit: (values: PinFormValues) => Promise<void>;
  onCancel: () => void;
}

const STATUS_OPTIONS: ReadonlyArray<{ value: PinStatus; label: string }> = [
  { value: 'WISH', label: '가고 싶어요' },
  { value: 'VISITED', label: '다녀왔어요' },
];

export default function PinForm({ initial, submitLabel, onSubmit, onCancel }: Props) {
  const nameId = useId();
  const memoId = useId();
  const [values, setValues] = useState<PinFormValues>(initial);
  const [submitted, setSubmitted] = useState(false);
  const [saving, setSaving] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const errors = submitted ? validatePinForm(values) : {};

  const update = <K extends keyof PinFormValues>(key: K, value: PinFormValues[K]) => setValues((prev) => ({ ...prev, [key]: value }));

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitted(true);
    if (Object.keys(validatePinForm(values)).length > 0) {
      return;
    }
    setSaving(true);
    setServerError(null);
    try {
      await onSubmit(values);
    } catch (error) {
      setServerError(error instanceof Error ? error.message : '저장하지 못했어요.');
      setSaving(false);
    }
  };

  return (
    <form onSubmit={(event) => void submit(event)} className="flex flex-col gap-4" noValidate>
      <div>
        <label htmlFor={nameId} className="mb-1.5 block text-sm font-medium text-slate-300">이름</label>
        <input
          id={nameId}
          value={values.name}
          onChange={(event) => update('name', event.target.value)}
          maxLength={LIMITS.pinName + 20}
          placeholder="연남동 그 카페"
          autoFocus
          aria-invalid={errors.name !== undefined}
          className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2.5 text-slate-100 outline-none placeholder:text-slate-500 focus:border-teal-500"
        />
        {errors.name && <p role="alert" className="mt-1 text-xs text-rose-300">{errors.name}</p>}
      </div>

      <div role="radiogroup" aria-label="상태" className="grid grid-cols-2 gap-2">
        {STATUS_OPTIONS.map((option) => (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={values.status === option.value}
            onClick={() => update('status', option.value)}
            className={`rounded-lg border px-3 py-2 text-sm font-medium ${
              values.status === option.value
                ? option.value === 'WISH' ? 'border-amber-400 bg-amber-400/15 text-amber-200' : 'border-teal-400 bg-teal-400/15 text-teal-200'
                : 'border-slate-700 text-slate-400 hover:bg-slate-800'
            }`}
          >
            {option.label}
          </button>
        ))}
      </div>

      <div>
        <span className="mb-1.5 block text-sm font-medium text-slate-300">평점</span>
        <StarRating value={values.rating} onChange={(rating) => update('rating', rating)} />
      </div>

      <div>
        <label htmlFor={memoId} className="mb-1.5 block text-sm font-medium text-slate-300">메모 (공유돼요)</label>
        <textarea
          id={memoId}
          value={values.sharedMemo}
          onChange={(event) => update('sharedMemo', event.target.value)}
          rows={3}
          placeholder="웨이팅이 길어요, 파스타가 맛있어요…"
          aria-invalid={errors.sharedMemo !== undefined}
          className="w-full resize-none rounded-lg border border-slate-700 bg-slate-950 px-3 py-2.5 text-slate-100 outline-none placeholder:text-slate-500 focus:border-teal-500"
        />
        {errors.sharedMemo && <p role="alert" className="mt-1 text-xs text-rose-300">{errors.sharedMemo}</p>}
      </div>

      <TagInput tags={values.tags} onChange={(tags) => update('tags', tags)} />

      {serverError && <p role="alert" className="rounded-lg bg-rose-500/15 px-3 py-2 text-sm text-rose-200">{serverError}</p>}

      <div className="flex gap-2">
        <button type="button" onClick={onCancel} className="flex-1 rounded-lg border border-slate-700 py-2.5 text-sm font-medium text-slate-300 hover:bg-slate-800">
          취소
        </button>
        <button type="submit" disabled={saving} className="flex-[2] rounded-lg bg-teal-500 py-2.5 text-sm font-semibold text-slate-950 hover:bg-teal-400 disabled:opacity-60">
          {saving ? '저장 중…' : submitLabel}
        </button>
      </div>
    </form>
  );
}
