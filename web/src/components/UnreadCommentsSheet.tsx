import type { UnreadPin } from '../api/types';
import Sheet from './Sheet';

interface Props {
  unread: UnreadPin[];
  mapNames: Map<string, string>;
  onPick: (item: UnreadPin) => void;
  onClose: () => void;
}

/** 내가 꽂았거나 내가 댓글을 남긴 핀 중 새 댓글이 달린 곳. 누르면 그 핀을 열어 댓글을 읽는다. */
export default function UnreadCommentsSheet({ unread, mapNames, onPick, onClose }: Props) {
  return (
    <Sheet title="새 댓글" onClose={onClose}>
      {unread.length === 0 ? (
        <p className="text-sm text-slate-400">새 댓글이 없어요. 내가 꽂은 핀이나 내가 댓글을 남긴 핀에 댓글이 달리면 여기에 모여요.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {unread.map((item) => (
            <li key={item.pinId}>
              <button type="button" onClick={() => onPick(item)} className="flex w-full items-center justify-between gap-3 rounded-xl bg-slate-800/60 p-3 text-left hover:bg-slate-800">
                <span className="flex min-w-0 flex-col gap-0.5">
                  <span className="truncate text-sm font-semibold text-slate-100">{item.pinName}</span>
                  <span className="truncate text-xs text-slate-500">{mapNames.get(item.mapId) ?? '지도'}</span>
                </span>
                <span className="shrink-0 rounded-full bg-teal-400/15 px-2.5 py-1 text-xs font-semibold text-teal-200">새 댓글 {item.unread}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </Sheet>
  );
}
