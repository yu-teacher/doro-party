import { useId, useState } from 'react';
import type { FormEvent } from 'react';
import { LIMITS } from '../api/types';
import Sheet from './Sheet';

interface Props {
  title: string;
  initialName: string;
  submitLabel: string;
  onSubmit: (name: string) => Promise<void>;
  onClose: () => void;
}

/** 모임 이름을 입력하는 시트(만들 때와 이름을 바꿀 때 같이 쓴다). */
export default function GroupNameSheet({ title, initialName, submitLabel, onSubmit, onClose }: Props) {
  const id = useId();
  const [name, setName] = useState(initialName);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const trimmed = name.trim();
    if (trimmed === '') {
      setError('모임 이름을 입력해 주세요.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await onSubmit(trimmed);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '저장하지 못했어요.');
      setSaving(false);
    }
  };

  return (
    <Sheet title={title} onClose={onClose}>
      <form onSubmit={(event) => void submit(event)} className="flex flex-col gap-4" noValidate>
        <div>
          <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-slate-300">모임 이름</label>
          <input id={id} value={name} onChange={(event) => setName(event.target.value)} maxLength={LIMITS.groupName} placeholder="토요일 홍대 모임" autoFocus
            className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2.5 text-slate-100 outline-none placeholder:text-slate-500 focus:border-teal-500" />
        </div>
        {error && <p role="alert" className="rounded-lg bg-rose-500/15 px-3 py-2 text-sm text-rose-200">{error}</p>}
        <div className="flex gap-2">
          <button type="button" onClick={onClose} className="flex-1 rounded-lg border border-slate-700 py-2.5 text-sm font-medium text-slate-300 hover:bg-slate-800">취소</button>
          <button type="submit" disabled={saving} className="flex-[2] rounded-lg bg-teal-500 py-2.5 text-sm font-semibold text-slate-950 hover:bg-teal-400 disabled:opacity-60">
            {saving ? '저장 중…' : submitLabel}
          </button>
        </div>
      </form>
    </Sheet>
  );
}
