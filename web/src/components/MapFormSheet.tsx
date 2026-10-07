import { useId, useState } from 'react';
import type { FormEvent } from 'react';
import { LIMITS } from '../api/types';
import type { PartyMap } from '../api/types';
import { useMapStore } from '../store/mapStore';
import Sheet from './Sheet';

interface Props {
  /** 있으면 이 지도를 수정하는 시트, 없으면 새 지도를 만드는 시트 */
  map: PartyMap | null;
  onClose: () => void;
}

export default function MapFormSheet({ map, onClose }: Props) {
  const nameId = useId();
  const descriptionId = useId();
  const { createMap, updateMap, removeMap } = useMapStore();
  const [name, setName] = useState(map?.name ?? '');
  const [description, setDescription] = useState(map?.description ?? '');
  const [saving, setSaving] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const trimmed = name.trim();
  const nameError = trimmed.length === 0 ? '지도 이름을 입력해 주세요.' : trimmed.length > LIMITS.mapName ? `이름은 ${LIMITS.mapName}자까지예요.` : null;
  const descriptionError = description.length > LIMITS.mapDescription ? `설명은 ${LIMITS.mapDescription}자까지예요.` : null;
  const [submitted, setSubmitted] = useState(false);

  const run = async (action: () => Promise<void>) => {
    setSaving(true);
    setError(null);
    try {
      await action();
      onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : '요청을 처리하지 못했어요.');
      setSaving(false);
    }
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setSubmitted(true);
    if (nameError || descriptionError) {
      return;
    }
    const input = { name: trimmed, description: description.trim() === '' ? null : description.trim() };
    void run(async () => {
      if (map) {
        await updateMap(map.id, input);
      } else {
        await createMap(input);
      }
    });
  };

  return (
    <Sheet title={map ? '지도 설정' : '새 지도'} onClose={onClose}>
      <form onSubmit={submit} className="flex flex-col gap-4" noValidate>
        <div>
          <label htmlFor={nameId} className="mb-1.5 block text-sm font-medium text-slate-300">지도 이름</label>
          <input
            id={nameId}
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="홍대 맛집, 일본 여행…"
            autoFocus
            className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3 py-2.5 text-slate-100 outline-none placeholder:text-slate-500 focus:border-teal-500"
          />
          {submitted && nameError && <p role="alert" className="mt-1 text-xs text-rose-300">{nameError}</p>}
        </div>
        <div>
          <label htmlFor={descriptionId} className="mb-1.5 block text-sm font-medium text-slate-300">설명 (선택)</label>
          <textarea
            id={descriptionId}
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            rows={2}
            className="w-full resize-none rounded-lg border border-slate-700 bg-slate-950 px-3 py-2.5 text-slate-100 outline-none focus:border-teal-500"
          />
          {submitted && descriptionError && <p role="alert" className="mt-1 text-xs text-rose-300">{descriptionError}</p>}
        </div>

        {error && <p role="alert" className="rounded-lg bg-rose-500/15 px-3 py-2 text-sm text-rose-200">{error}</p>}

        <div className="flex gap-2">
          <button type="button" onClick={onClose} className="flex-1 rounded-lg border border-slate-700 py-2.5 text-sm font-medium text-slate-300 hover:bg-slate-800">취소</button>
          <button type="submit" disabled={saving} className="flex-[2] rounded-lg bg-teal-500 py-2.5 text-sm font-semibold text-slate-950 hover:bg-teal-400 disabled:opacity-60">
            {saving ? '저장 중…' : map ? '저장' : '만들기'}
          </button>
        </div>

        {map && (
          confirmingDelete ? (
            <div className="rounded-lg bg-rose-500/10 p-3">
              <p className="mb-2 text-sm text-rose-100">「{map.name}」과 안에 꽂은 핀 {map.pinCount}개가 모두 지워져요. 되돌릴 수 없어요.</p>
              <div className="flex justify-end gap-2">
                <button type="button" onClick={() => setConfirmingDelete(false)} className="rounded-md px-3 py-1.5 text-sm text-slate-300 hover:bg-slate-800">취소</button>
                <button type="button" disabled={saving} onClick={() => void run(() => removeMap(map.id))} className="rounded-md bg-rose-500 px-3 py-1.5 text-sm font-semibold text-white hover:bg-rose-400 disabled:opacity-60">
                  지도 삭제
                </button>
              </div>
            </div>
          ) : (
            <button type="button" onClick={() => setConfirmingDelete(true)} className="self-start text-sm text-rose-300 hover:underline">이 지도 삭제…</button>
          )
        )}
      </form>
    </Sheet>
  );
}
