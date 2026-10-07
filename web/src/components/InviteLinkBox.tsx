import { Copy, Link2, RefreshCw, Send, Trash2 } from 'lucide-react';
import { useState } from 'react';
import type { InviteLink } from '../api/types';
import { formatDay } from '../utils/dates';
import { inviteUrl } from '../utils/inviteLink';
import type { InviteKind } from '../utils/inviteLink';
import { copyText, shareLink } from '../utils/share';

interface Props {
  kind: InviteKind;
  /** 현재 링크. 없거나 만료됐으면 null. */
  link: InviteLink | null;
  /** 링크를 만들고 다시 만들고 없앨 수 있는지(모임에서는 방장만) */
  canManage: boolean;
  /** 링크를 보낼 때 함께 가는 문구 */
  shareTitle: string;
  shareText: string;
  onCreate: () => Promise<void>;
  onRevoke: () => Promise<void>;
}

/** 초대 링크를 보여 주고 복사·공유·다시 만들기·없애기를 한다. */
export default function InviteLinkBox({ kind, link, canManage, shareTitle, shareText, onCreate, onRevoke }: Props) {
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const url = link ? inviteUrl(window.location.origin, kind, link.code) : null;

  const run = async (action: () => Promise<void>, failure: string) => {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      await action();
    } catch (e) {
      setError(e instanceof Error ? e.message : failure);
    } finally {
      setBusy(false);
    }
  };

  const copy = async () => {
    if (url) {
      setNotice((await copyText(url)) ? '링크를 복사했어요.' : '복사하지 못했어요. 링크를 길게 눌러 복사해 주세요.');
    }
  };

  const send = async () => {
    if (!url) {
      return;
    }
    const outcome = await shareLink(url, shareTitle, shareText);
    if (outcome === 'copied') {
      setNotice('링크를 복사했어요.');
    } else if (outcome === 'failed') {
      setNotice('보내지 못했어요. 링크를 길게 눌러 복사해 주세요.');
    }
  };

  return (
    <div className="flex flex-col gap-2 rounded-xl bg-slate-800/60 p-3">
      <div className="flex items-center gap-2 text-sm font-semibold text-slate-200"><Link2 size={15} /> 초대 링크</div>

      {url && link ? (
        <>
          <p className="break-all rounded-lg bg-slate-950 px-3 py-2 text-xs text-slate-300">{url}</p>
          <p className="text-xs text-slate-500">{formatDay(link.expiresAt.slice(0, 10))}까지 쓸 수 있어요.</p>
          <div className="flex gap-2">
            <button type="button" onClick={() => void send()} className="flex flex-1 items-center justify-center gap-1.5 rounded-lg bg-teal-500 py-2 text-sm font-semibold text-slate-950 hover:bg-teal-400">
              <Send size={14} /> 보내기
            </button>
            <button type="button" onClick={() => void copy()} className="flex flex-1 items-center justify-center gap-1.5 rounded-lg border border-slate-600 py-2 text-sm font-medium text-slate-200 hover:bg-slate-800">
              <Copy size={14} /> 복사
            </button>
          </div>
          {canManage && (
            <div className="flex justify-end gap-1 text-xs">
              <button type="button" disabled={busy} onClick={() => void run(onCreate, '다시 만들지 못했어요.')} className="flex items-center gap-1 rounded px-2 py-1 text-slate-400 hover:bg-slate-800 hover:text-slate-200 disabled:opacity-60">
                <RefreshCw size={12} /> 다시 만들기
              </button>
              <button type="button" disabled={busy} onClick={() => void run(onRevoke, '없애지 못했어요.')} className="flex items-center gap-1 rounded px-2 py-1 text-slate-400 hover:bg-slate-800 hover:text-rose-300 disabled:opacity-60">
                <Trash2 size={12} /> 없애기
              </button>
            </div>
          )}
        </>
      ) : canManage ? (
        <button type="button" disabled={busy} onClick={() => void run(onCreate, '링크를 만들지 못했어요.')} className="rounded-lg bg-teal-500 py-2 text-sm font-semibold text-slate-950 hover:bg-teal-400 disabled:opacity-60">
          {busy ? '만드는 중…' : '초대 링크 만들기'}
        </button>
      ) : (
        <p className="text-xs text-slate-500">아직 초대 링크가 없어요. 방장이 만들면 여기에 보여요.</p>
      )}

      {notice && <p role="status" className="text-xs text-teal-300">{notice}</p>}
      {error && <p role="alert" className="text-xs text-rose-300">{error}</p>}
    </div>
  );
}
