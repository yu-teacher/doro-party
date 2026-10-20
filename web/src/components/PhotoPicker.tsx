import { Camera, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import type { ChangeEvent } from 'react';
import { LIMITS } from '../api/types';

interface Props {
  files: File[];
  onChange: (files: File[]) => void;
  disabled?: boolean;
}

/** 새 핀에 붙일 사진을 고른다. 핀을 저장한 직후 이 사진들이 올라간다. 썸네일로 미리 보고 하나씩 뺄 수 있다. */
export default function PhotoPicker({ files, onChange, disabled = false }: Props) {
  const input = useRef<HTMLInputElement>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [previews, setPreviews] = useState<Array<{ file: File; url: string }>>([]);

  // 미리보기 주소는 파일이 바뀔 때 다시 만들고, 쓰지 않게 되면(빼거나 닫으면) 반드시 해제한다
  useEffect(() => {
    const created = files.map((file) => ({ file, url: URL.createObjectURL(file) }));
    setPreviews(created);
    return () => created.forEach((preview) => URL.revokeObjectURL(preview.url));
  }, [files]);

  const onPick = (event: ChangeEvent<HTMLInputElement>) => {
    const picked = Array.from(event.target.files ?? []);
    event.target.value = '';
    if (picked.length === 0) {
      return;
    }
    const room = LIMITS.photosPerPin - files.length;
    setNotice(picked.length > room ? `사진은 핀마다 ${LIMITS.photosPerPin}장까지 붙일 수 있어요.` : null);
    onChange([...files, ...picked.slice(0, Math.max(0, room))]);
  };

  const remove = (index: number) => {
    setNotice(null);
    onChange(files.filter((_, position) => position !== index));
  };

  const full = files.length >= LIMITS.photosPerPin;

  return (
    <section aria-label="사진">
      <div className="mb-1.5 flex items-baseline justify-between">
        <span className="text-sm font-medium text-slate-300">사진</span>
        {files.length > 0 && <span className="text-xs text-slate-500">{files.length}/{LIMITS.photosPerPin}장</span>}
      </div>
      <div className="flex gap-2 overflow-x-auto pb-1 pr-2 pt-2">
        {previews.map((preview, index) => (
          <div key={`${preview.file.name}-${preview.file.size}-${preview.file.lastModified}-${index}`} className="relative shrink-0">
            <img src={preview.url} alt="" className="h-20 w-20 rounded-lg object-cover ring-1 ring-slate-700" />
            <button type="button" onClick={() => remove(index)} disabled={disabled} aria-label={`사진 ${index + 1} 빼기`}
              className="absolute -right-1.5 -top-1.5 rounded-full bg-slate-950 p-1.5 text-slate-200 ring-1 ring-slate-600 hover:text-rose-300 disabled:opacity-60">
              <X size={14} />
            </button>
          </div>
        ))}
        {!full && (
          <button type="button" onClick={() => input.current?.click()} disabled={disabled}
            className="flex h-20 w-20 shrink-0 flex-col items-center justify-center gap-1 rounded-lg border border-dashed border-slate-600 text-xs text-slate-400 hover:border-teal-500 hover:text-teal-300 disabled:opacity-60">
            <Camera size={20} />
            사진 추가
          </button>
        )}
      </div>
      <input ref={input} type="file" accept="image/*" multiple className="sr-only" tabIndex={-1} aria-label="사진 고르기" onChange={onPick} />
      {notice && <p role="status" className="mt-1 text-xs text-amber-300">{notice}</p>}
    </section>
  );
}
