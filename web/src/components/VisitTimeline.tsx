import { Plus, Trash2 } from 'lucide-react';
import { useId, useState } from 'react';
import type { FormEvent } from 'react';
import { LIMITS } from '../api/types';
import type { Visit } from '../api/types';
import { formatDay, todayInServiceZone } from '../utils/dates';

interface Props {
  visits: Visit[];
  /** 내가 남긴 기록이거나 내가 지도 주인이어야 지울 수 있다 */
  canDelete: (visit: Visit) => boolean;
  onAdd: (visitedOn: string, note: string | null) => Promise<void>;
  onDelete: (visitId: string) => Promise<void>;
}

/** 이 핀을 다녀온 날짜와 한 줄 후기의 타임라인. */
export default function VisitTimeline({ visits, canDelete, onAdd, onDelete }: Props) {
  const dateId = useId();
  const noteId = useId();
  const [adding, setAdding] = useState(false);
  const [date, setDate] = useState(() => todayInServiceZone());
  const [note, setNote] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const today = todayInServiceZone();

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (date === '' || date > today) {
      setError('오늘 이후 날짜는 기록할 수 없어요.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await onAdd(date, note.trim() === '' ? null : note.trim());
      setAdding(false);
      setNote('');
      setDate(todayInServiceZone());
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '기록하지 못했어요.');
    } finally {
      setSaving(false);
    }
  };

  const remove = async (visitId: string) => {
    setError(null);
    try {
      await onDelete(visitId);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '지우지 못했어요.');
    }
  };

  return (
    <section aria-label="방문 기록">
      <div className="mb-2 flex items-center justify-between">
        <h3 className="text-sm font-semibold text-slate-200">방문 기록 {visits.length > 0 && <span className="text-slate-500">({visits.length})</span>}</h3>
        {!adding && (
          <button type="button" onClick={() => setAdding(true)} className="flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-teal-300 hover:bg-slate-800">
            <Plus size={14} /> 다녀왔어요
          </button>
        )}
      </div>

      {adding && (
        <form onSubmit={(event) => void submit(event)} className="mb-3 flex flex-col gap-2 rounded-lg bg-slate-800/60 p-3">
          <div className="flex items-center gap-2">
            <label htmlFor={dateId} className="shrink-0 text-xs text-slate-400">날짜</label>
            <input id={dateId} type="date" value={date} max={today} onChange={(event) => setDate(event.target.value)}
              className="min-w-0 flex-1 rounded-md border border-slate-700 bg-slate-950 px-2 py-1.5 text-sm text-slate-100 outline-none focus:border-teal-500" />
          </div>
          <label htmlFor={noteId} className="sr-only">한 줄 후기</label>
          <input id={noteId} value={note} onChange={(event) => setNote(event.target.value)} maxLength={LIMITS.visitNote} placeholder="한 줄 후기 (선택)"
            className="rounded-md border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-sm text-slate-100 outline-none placeholder:text-slate-500 focus:border-teal-500" />
          <div className="flex justify-end gap-2">
            <button type="button" onClick={() => { setAdding(false); setError(null); }} className="rounded-md px-3 py-1.5 text-sm text-slate-300 hover:bg-slate-800">취소</button>
            <button type="submit" disabled={saving} className="rounded-md bg-teal-500 px-3 py-1.5 text-sm font-semibold text-slate-950 hover:bg-teal-400 disabled:opacity-60">
              {saving ? '저장 중…' : '기록'}
            </button>
          </div>
        </form>
      )}

      {error && <p role="alert" className="mb-2 rounded-lg bg-rose-500/15 px-3 py-2 text-xs text-rose-200">{error}</p>}

      {visits.length === 0 ? (
        <p className="text-xs text-slate-500">아직 방문 기록이 없어요.</p>
      ) : (
        <ol className="flex flex-col gap-2 border-l border-slate-700 pl-3">
          {visits.map((visit) => (
            <li key={visit.id} className="flex items-start justify-between gap-2">
              <div className="min-w-0">
                <p className="text-sm font-medium text-slate-100">{formatDay(visit.visitedOn)}</p>
                {visit.note && <p className="break-words text-sm text-slate-400">{visit.note}</p>}
              </div>
              {canDelete(visit) && (
                <button type="button" onClick={() => void remove(visit.id)} className="shrink-0 rounded p-1.5 text-slate-500 hover:bg-slate-800 hover:text-rose-300" aria-label={`${formatDay(visit.visitedOn)} 기록 지우기`}>
                  <Trash2 size={14} />
                </button>
              )}
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}
