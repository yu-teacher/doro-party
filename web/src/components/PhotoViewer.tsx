import { ChevronLeft, ChevronRight, Trash2, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { photoUrl } from '../api/recordsApi';
import type { Photo } from '../api/types';

interface Props {
  mapId: string;
  pinId: string;
  photos: Photo[];
  startIndex: number;
  canDelete: (photo: Photo) => boolean;
  onDelete: (photoId: string) => Promise<void>;
  onClose: () => void;
}

/** 사진을 크게 보는 전체 화면. 좌우 화살표 키와 버튼으로 넘기고, Esc 로 닫는다. */
export default function PhotoViewer({ mapId, pinId, photos, startIndex, canDelete, onDelete, onClose }: Props) {
  const [index, setIndex] = useState(startIndex);
  const [confirming, setConfirming] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const photo = photos[Math.min(index, photos.length - 1)];

  useEffect(() => {
    if (photos.length === 0) {
      onClose();
    }
  }, [photos.length, onClose]);

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      } else if (event.key === 'ArrowLeft') {
        setIndex((current) => Math.max(0, current - 1));
      } else if (event.key === 'ArrowRight') {
        setIndex((current) => Math.min(photos.length - 1, current + 1));
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose, photos.length]);

  if (!photo) {
    return null;
  }

  const remove = async () => {
    setBusy(true);
    setError(null);
    try {
      await onDelete(photo.id);
      // 지우고 나면 전체 화면을 닫고 핀 상세로 돌아간다(다음 사진이 자동으로 이어 보이지 않게)
      onClose();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '사진을 지우지 못했어요.');
      setBusy(false);
    }
  };

  return (
    <div role="dialog" aria-modal="true" aria-label="사진 보기" className="fixed inset-0 z-50 flex flex-col bg-black/95">
      <div className="flex items-center justify-between px-4 pb-2 pt-[calc(env(safe-area-inset-top)+0.75rem)] text-slate-200">
        <span className="text-sm">{index + 1} / {photos.length}</span>
        <button type="button" onClick={onClose} className="rounded-full p-2 hover:bg-white/10" aria-label="닫기"><X size={22} /></button>
      </div>

      <div className="relative flex min-h-0 flex-1 items-center justify-center">
        <img src={photoUrl(mapId, pinId, photo.id)} alt="핀에 붙인 사진" className="max-h-full max-w-full object-contain" />
        {index > 0 && (
          <button type="button" onClick={() => setIndex(index - 1)} className="absolute left-2 rounded-full bg-black/50 p-2 text-white hover:bg-black/70" aria-label="이전 사진">
            <ChevronLeft size={24} />
          </button>
        )}
        {index < photos.length - 1 && (
          <button type="button" onClick={() => setIndex(index + 1)} className="absolute right-2 rounded-full bg-black/50 p-2 text-white hover:bg-black/70" aria-label="다음 사진">
            <ChevronRight size={24} />
          </button>
        )}
      </div>

      <div className="px-4 pb-[calc(env(safe-area-inset-bottom)+1rem)] pt-3">
        {error && <p role="alert" className="mb-2 rounded-lg bg-rose-500/20 px-3 py-2 text-sm text-rose-100">{error}</p>}
        {canDelete(photo) && (
          confirming ? (
            <div className="flex items-center justify-center gap-3 text-sm">
              <span className="text-rose-100">이 사진을 지울까요?</span>
              <button type="button" onClick={() => setConfirming(false)} className="rounded-md px-3 py-1.5 text-slate-300 hover:bg-white/10">취소</button>
              <button type="button" disabled={busy} onClick={() => void remove()} className="rounded-md bg-rose-500 px-3 py-1.5 font-semibold text-white hover:bg-rose-400 disabled:opacity-60">
                {busy ? '지우는 중…' : '지우기'}
              </button>
            </div>
          ) : (
            <button type="button" onClick={() => setConfirming(true)} className="mx-auto flex items-center gap-1.5 rounded-lg px-3 py-2 text-sm text-rose-300 hover:bg-white/10">
              <Trash2 size={16} /> 사진 삭제
            </button>
          )
        )}
      </div>
    </div>
  );
}
