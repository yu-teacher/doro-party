interface Props {
  onConfirm: () => void;
  onCancel: () => void;
}

/** 핀을 꽂을 위치를 고르는 동안 지도 아래에 뜨는 작은 확인 바. 지도를 가리지 않고, 지도를 눌러 위치를 옮길 수 있다. */
export default function PlacementBar({ onConfirm, onCancel }: Props) {
  return (
    <div role="region" aria-label="핀 위치 선택" className="absolute inset-x-3 bottom-4 z-10 mx-auto max-w-md rounded-xl bg-slate-900/95 p-3 shadow-xl ring-1 ring-slate-700">
      <p className="mb-2 text-center text-xs text-slate-400">지도를 눌러 위치를 옮길 수 있어요</p>
      <div className="flex gap-2">
        <button type="button" onClick={onCancel} className="flex-1 rounded-lg border border-slate-700 py-2.5 text-sm font-medium text-slate-300 hover:bg-slate-800">
          취소
        </button>
        <button type="button" onClick={onConfirm} className="flex-[2] rounded-lg bg-teal-500 py-2.5 text-sm font-semibold text-slate-950 hover:bg-teal-400">
          이 위치에 핀 꽂기
        </button>
      </div>
    </div>
  );
}
