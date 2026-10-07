import { Pencil, Trash2 } from 'lucide-react';
import { useState } from 'react';
import type { Pin, RevisitIntent } from '../api/types';
import { usePinRecords } from '../hooks/usePinRecords';
import { useAuthStore } from '../store/authStore';
import { useMapStore } from '../store/mapStore';
import { canPlacePins } from '../utils/mapRole';
import { EMPTY_PIN_FORM, formValuesFromPin, toPinInput } from '../utils/pinForm';
import type { PinFormValues } from '../utils/pinForm';
import PhotoStrip from './PhotoStrip';
import PinForm from './PinForm';
import PrivateNoteBox from './PrivateNoteBox';
import Sheet from './Sheet';
import StarRating from './StarRating';
import UserDot from './UserDot';
import VisitTimeline from './VisitTimeline';

interface CreateProps {
  mode: 'create';
  lat: number;
  lng: number;
  onCreated: (pin: Pin) => void;
  onClose: () => void;
}

interface ViewProps {
  mode: 'view';
  pin: Pin;
  onClose: () => void;
}

const STATUS_LABEL = { WISH: '가고 싶어요', VISITED: '다녀왔어요' } as const;
const REVISIT_LABEL: Record<RevisitIntent, string> = { AGAIN: '또 가고 싶어요', ONCE: '한 번이면 충분' };

function formatDate(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' });
}

export default function PinSheet(props: CreateProps | ViewProps) {
  return props.mode === 'create' ? <CreatePin {...props} /> : <ViewPin {...props} />;
}

function CreatePin({ lat, lng, onCreated, onClose }: CreateProps) {
  const addPin = useMapStore((state) => state.addPin);
  const save = async (values: PinFormValues) => {
    onCreated(await addPin(toPinInput(values, lat, lng)));
  };
  return (
    <Sheet title="새 핀" onClose={onClose}>
      <PinForm initial={EMPTY_PIN_FORM} submitLabel="핀 꽂기" onSubmit={save} onCancel={onClose} />
    </Sheet>
  );
}

function ViewPin({ pin, onClose }: ViewProps) {
  const { editPin, removePin, savePrivateNote, deletePrivateNote } = useMapStore.getState();
  const isMapOwner = useMapStore((state) => state.maps.find((map) => map.id === pin.mapId)?.mine ?? false);
  const role = useMapStore((state) => state.maps.find((map) => map.id === pin.mapId)?.role);
  const privateNote = useMapStore((state) => state.privateNotes[pin.id]);
  const currentUserId = useAuthStore((state) => state.user?.id ?? null);
  const records = usePinRecords(pin.mapId, pin.id);
  const [editing, setEditing] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 서버가 최종 판단하지만, 못 할 일은 버튼을 보여 주지 않는다: 내가 꽂은 핀이거나 내가 지도 주인일 때만 고치고 사진을 붙인다
  const canModifyPin = isMapOwner || pin.createdBy === currentUserId;

  const save = async (values: PinFormValues) => {
    await editPin(pin.id, toPinInput(values, pin.lat, pin.lng));
    setEditing(false);
  };

  const remove = async () => {
    setDeleting(true);
    setError(null);
    try {
      await removePin(pin.id);
      onClose();
    } catch (e) {
      setError(e instanceof Error ? e.message : '삭제하지 못했어요.');
      setDeleting(false);
    }
  };

  if (editing) {
    return (
      <Sheet title="핀 수정" onClose={() => setEditing(false)}>
        <PinForm initial={formValuesFromPin(pin)} submitLabel="저장" onSubmit={save} onCancel={() => setEditing(false)} />
      </Sheet>
    );
  }

  return (
    <Sheet title={pin.name} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <div className="flex flex-wrap items-center gap-2">
          <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${pin.status === 'WISH' ? 'bg-amber-400/15 text-amber-200' : 'bg-teal-400/15 text-teal-200'}`}>
            {STATUS_LABEL[pin.status]}
          </span>
          {pin.revisitIntent && <span className="rounded-full bg-sky-300/15 px-2.5 py-1 text-xs font-semibold text-sky-100">{REVISIT_LABEL[pin.revisitIntent]}</span>}
          {pin.rating !== null && <StarRating value={pin.rating} size={16} />}
        </div>

        {pin.createdBy !== currentUserId && (
          <UserDot nickname={pin.authorNickname} color={pin.authorColor} note="님이 꽂았어요" />
        )}

        <PhotoStrip
          mapId={pin.mapId}
          pinId={pin.id}
          photos={records.photos}
          canAdd={canModifyPin}
          canDelete={(photo) => isMapOwner || photo.uploadedBy === currentUserId}
          onUpload={records.uploadPhotos}
          onDelete={records.removePhoto}
        />

        {pin.sharedMemo && <p className="whitespace-pre-wrap text-sm leading-relaxed text-slate-200">{pin.sharedMemo}</p>}
        {pin.tags.length > 0 && (
          <ul className="flex flex-wrap gap-1.5">
            {pin.tags.map((tag) => <li key={tag} className="rounded-full bg-slate-800 px-2.5 py-0.5 text-sm text-slate-300">#{tag}</li>)}
          </ul>
        )}

        {records.error && <p role="alert" className="rounded-lg bg-rose-500/15 px-3 py-2 text-xs text-rose-200">{records.error}</p>}

        <VisitTimeline
          visits={records.visits}
          canAdd={canPlacePins(role)}
          canDelete={(visit) => isMapOwner || visit.userId === currentUserId}
          onAdd={records.addVisit}
          onDelete={records.removeVisit}
        />

        <PrivateNoteBox
          key={pin.id}
          saved={privateNote}
          onSave={(body) => savePrivateNote(pin.id, body)}
          onDelete={() => deletePrivateNote(pin.id)}
        />

        <p className="text-xs text-slate-500">{formatDate(pin.createdAt)}에 꽂았어요</p>

        {error && <p role="alert" className="rounded-lg bg-rose-500/15 px-3 py-2 text-sm text-rose-200">{error}</p>}

        {canModifyPin && (confirmingDelete ? (
          <div className="flex items-center gap-2 rounded-lg bg-rose-500/10 p-3">
            <p className="flex-1 text-sm text-rose-100">이 핀과 사진·기록을 모두 지울까요? 되돌릴 수 없어요.</p>
            <button type="button" onClick={() => setConfirmingDelete(false)} className="rounded-md px-3 py-1.5 text-sm text-slate-300 hover:bg-slate-800">취소</button>
            <button type="button" disabled={deleting} onClick={() => void remove()} className="rounded-md bg-rose-500 px-3 py-1.5 text-sm font-semibold text-white hover:bg-rose-400 disabled:opacity-60">
              {deleting ? '지우는 중…' : '지우기'}
            </button>
          </div>
        ) : (
          <div className="flex gap-2">
            <button type="button" onClick={() => setEditing(true)} className="flex flex-1 items-center justify-center gap-1.5 rounded-lg border border-slate-700 py-2.5 text-sm font-medium text-slate-200 hover:bg-slate-800">
              <Pencil size={15} /> 수정
            </button>
            <button type="button" onClick={() => setConfirmingDelete(true)} className="flex flex-1 items-center justify-center gap-1.5 rounded-lg border border-slate-700 py-2.5 text-sm font-medium text-rose-300 hover:bg-slate-800">
              <Trash2 size={15} /> 삭제
            </button>
          </div>
        ))}
      </div>
    </Sheet>
  );
}
