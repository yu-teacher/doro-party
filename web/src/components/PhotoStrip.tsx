import { Camera } from 'lucide-react';
import { useRef, useState } from 'react';
import type { ChangeEvent } from 'react';
import { photoUrl } from '../api/recordsApi';
import type { Photo } from '../api/types';
import PhotoViewer from './PhotoViewer';

interface Props {
  mapId: string;
  pinId: string;
  photos: Photo[];
  /** 사진을 붙일 수 있는지(내가 꽂은 핀이거나 지도 주인) */
  canAdd: boolean;
  canDelete: (photo: Photo) => boolean;
  onUpload: (files: File[]) => Promise<string[]>;
  onDelete: (photoId: string) => Promise<void>;
}

/** 핀의 사진 줄: 작은 사진을 가로로 보여 주고, 누르면 크게 본다. */
export default function PhotoStrip({ mapId, pinId, photos, canAdd, canDelete, onUpload, onDelete }: Props) {
  const input = useRef<HTMLInputElement>(null);
  const [viewing, setViewing] = useState<number | null>(null);
  const [uploading, setUploading] = useState(false);
  const [problems, setProblems] = useState<string[]>([]);

  const onPick = async (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? []);
    event.target.value = '';
    if (files.length === 0) {
      return;
    }
    setUploading(true);
    setProblems([]);
    try {
      setProblems(await onUpload(files));
    } finally {
      setUploading(false);
    }
  };

  if (photos.length === 0 && !canAdd) {
    return null;
  }

  return (
    <section aria-label="사진">
      <div className="flex gap-2 overflow-x-auto pb-1">
        {photos.map((photo, index) => (
          <button key={photo.id} type="button" onClick={() => setViewing(index)} className="shrink-0 overflow-hidden rounded-lg ring-1 ring-slate-700 hover:ring-teal-500" aria-label={`사진 ${index + 1} 크게 보기`}>
            <img src={photoUrl(mapId, pinId, photo.id)} alt="" loading="lazy" className="h-20 w-20 object-cover" />
          </button>
        ))}
        {canAdd && (
          <button
            type="button"
            onClick={() => input.current?.click()}
            disabled={uploading}
            className="flex h-20 w-20 shrink-0 flex-col items-center justify-center gap-1 rounded-lg border border-dashed border-slate-600 text-xs text-slate-400 hover:border-teal-500 hover:text-teal-300 disabled:opacity-60"
          >
            <Camera size={20} />
            {uploading ? '올리는 중…' : '사진 추가'}
          </button>
        )}
        <input ref={input} type="file" accept="image/*" multiple hidden onChange={(event) => void onPick(event)} />
      </div>
      {problems.length > 0 && (
        <ul role="alert" className="mt-2 flex flex-col gap-1 rounded-lg bg-rose-500/15 px-3 py-2 text-xs text-rose-200">
          {problems.map((problem) => <li key={problem}>{problem}</li>)}
        </ul>
      )}
      {viewing !== null && (
        <PhotoViewer mapId={mapId} pinId={pinId} photos={photos} startIndex={viewing} canDelete={canDelete} onDelete={onDelete} onClose={() => setViewing(null)} />
      )}
    </section>
  );
}
