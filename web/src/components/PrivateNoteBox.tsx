import { Lock } from 'lucide-react';
import { useId, useState } from 'react';
import { LIMITS } from '../api/types';

interface Props {
  /** 저장돼 있는 내 메모(없으면 undefined) */
  saved: string | undefined;
  onSave: (body: string) => Promise<void>;
  onDelete: () => Promise<void>;
}

/** 나만 보는 메모. 지도를 같이 보는 친구에게도 보이지 않는다. */
export default function PrivateNoteBox({ saved, onSave, onDelete }: Props) {
  const id = useId();
  const [draft, setDraft] = useState(saved ?? '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const trimmed = draft.trim();
  const changed = trimmed !== (saved ?? '');

  const run = async (action: () => Promise<void>, failure: string) => {
    setBusy(true);
    setError(null);
    try {
      await action();
    } catch (e) {
      setError(e instanceof Error ? e.message : failure);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section aria-label="나만 보는 메모">
      <label htmlFor={id} className="mb-1.5 flex items-center gap-1.5 text-sm font-semibold text-slate-200">
        <Lock size={13} className="text-slate-400" /> 나만 보는 메모
      </label>
      <textarea
        id={id}
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        rows={2}
        maxLength={LIMITS.privateNote}
        placeholder="친구에게는 보이지 않아요"
        className="w-full resize-none rounded-lg border border-slate-700 bg-slate-950 px-3 py-2 text-sm text-slate-100 outline-none placeholder:text-slate-500 focus:border-teal-500"
      />
      {error && <p role="alert" className="mt-1 text-xs text-rose-300">{error}</p>}
      <div className="mt-1.5 flex justify-end gap-2">
        {saved !== undefined && (
          <button type="button" disabled={busy} onClick={() => void run(async () => { await onDelete(); setDraft(''); }, '지우지 못했어요.')}
            className="rounded-md px-3 py-1.5 text-xs text-slate-400 hover:bg-slate-800 disabled:opacity-60">
            지우기
          </button>
        )}
        <button type="button" disabled={busy || !changed || trimmed === ''} onClick={() => void run(() => onSave(trimmed), '저장하지 못했어요.')}
          className="rounded-md bg-slate-700 px-3 py-1.5 text-xs font-semibold text-slate-100 hover:bg-slate-600 disabled:opacity-40">
          {busy ? '저장 중…' : saved !== undefined && !changed ? '저장됨' : '저장'}
        </button>
      </div>
    </section>
  );
}
