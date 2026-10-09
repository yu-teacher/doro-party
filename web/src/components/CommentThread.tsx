import { Check, Pencil, Send, Trash2, X } from 'lucide-react';
import { useId, useState } from 'react';
import type { FormEvent } from 'react';
import { LIMITS } from '../api/types';
import type { PinComment } from '../api/types';
import { formatRelative } from '../utils/dates';
import UserDot from './UserDot';

interface Props {
  comments: PinComment[];
  loading: boolean;
  /** 댓글을 불러오지 못했을 때의 안내 문구 */
  loadError: string | null;
  currentUserId: string | null;
  /** 지울 수 있는지: 내가 쓴 댓글이거나, 내가 이 핀을 꽂았거나, 내가 지도 주인일 때 */
  canDelete: (comment: PinComment) => boolean;
  onAdd: (body: string) => Promise<void>;
  onEdit: (commentId: string, body: string) => Promise<void>;
  onDelete: (commentId: string) => Promise<void>;
}

/** 핀에 남긴 댓글. 지도를 볼 수 있는 사람이면 누구나 쓰고, 고치는 것은 쓴 사람만 한다. */
export default function CommentThread({ comments, loading, loadError, currentUserId, canDelete, onAdd, onEdit, onDelete }: Props) {
  const composerId = useId();
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editDraft, setEditDraft] = useState('');
  const [error, setError] = useState<string | null>(null);

  const run = async (action: () => Promise<void>, failure: string): Promise<boolean> => {
    setError(null);
    try {
      await action();
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : failure);
      return false;
    }
  };

  const send = async (event: FormEvent) => {
    event.preventDefault();
    const body = draft.trim();
    if (body === '' || sending) {
      return;
    }
    setSending(true);
    if (await run(() => onAdd(body), '댓글을 남기지 못했어요.')) {
      setDraft('');
    }
    setSending(false);
  };

  const saveEdit = async (commentId: string) => {
    const body = editDraft.trim();
    if (body === '') {
      return;
    }
    if (await run(() => onEdit(commentId, body), '댓글을 고치지 못했어요.')) {
      setEditingId(null);
    }
  };

  return (
    <section aria-label="댓글">
      <h3 className="mb-2 text-sm font-semibold text-slate-200">댓글 {comments.length > 0 && <span className="text-slate-500">({comments.length})</span>}</h3>

      {loadError && <p role="alert" className="mb-2 rounded-lg bg-rose-500/15 px-3 py-2 text-xs text-rose-200">{loadError}</p>}
      {error && <p role="alert" className="mb-2 rounded-lg bg-rose-500/15 px-3 py-2 text-xs text-rose-200">{error}</p>}

      {loading ? (
        <p className="text-xs text-slate-500">댓글을 불러오는 중…</p>
      ) : comments.length === 0 ? (
        <p className="mb-2 text-xs text-slate-500">아직 댓글이 없어요. 이 장소에 대해 한마디 남겨 보세요.</p>
      ) : (
        <ul className="mb-3 flex flex-col gap-3">
          {comments.map((comment) => {
            const mine = comment.userId === currentUserId;
            const editing = editingId === comment.id;
            return (
              <li key={comment.id} className="flex flex-col gap-1">
                <div className="flex items-center justify-between gap-2 text-xs text-slate-500">
                  <span className="flex min-w-0 items-center gap-2">
                    <UserDot nickname={comment.authorNickname} color={comment.authorColor} />
                    <span className="shrink-0">{formatRelative(comment.createdAt)}{comment.editedAt && ' · 수정됨'}</span>
                  </span>
                  {!editing && (
                    <span className="flex shrink-0 items-center">
                      {mine && (
                        <button type="button" onClick={() => { setEditingId(comment.id); setEditDraft(comment.body); setError(null); }}
                          className="rounded p-1.5 text-slate-500 hover:bg-slate-800 hover:text-slate-200" aria-label="댓글 고치기">
                          <Pencil size={14} />
                        </button>
                      )}
                      {canDelete(comment) && (
                        <button type="button" onClick={() => void run(() => onDelete(comment.id), '댓글을 지우지 못했어요.')}
                          className="rounded p-1.5 text-slate-500 hover:bg-slate-800 hover:text-rose-300" aria-label="댓글 지우기">
                          <Trash2 size={14} />
                        </button>
                      )}
                    </span>
                  )}
                </div>
                {editing ? (
                  <div className="flex flex-col gap-2">
                    <label htmlFor={`${composerId}-edit`} className="sr-only">댓글 고치기</label>
                    <textarea id={`${composerId}-edit`} value={editDraft} onChange={(event) => setEditDraft(event.target.value)} maxLength={LIMITS.comment} rows={2}
                      className="rounded-md border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-sm text-slate-100 outline-none focus:border-teal-500" />
                    <div className="flex justify-end gap-2">
                      <button type="button" onClick={() => setEditingId(null)} className="flex items-center gap-1 rounded-md px-3 py-1.5 text-sm text-slate-300 hover:bg-slate-800">
                        <X size={14} /> 취소
                      </button>
                      <button type="button" onClick={() => void saveEdit(comment.id)} disabled={editDraft.trim() === ''}
                        className="flex items-center gap-1 rounded-md bg-teal-500 px-3 py-1.5 text-sm font-semibold text-slate-950 hover:bg-teal-400 disabled:opacity-60">
                        <Check size={14} /> 저장
                      </button>
                    </div>
                  </div>
                ) : (
                  <p className="whitespace-pre-wrap break-words text-sm leading-relaxed text-slate-200">{comment.body}</p>
                )}
              </li>
            );
          })}
        </ul>
      )}

      <form onSubmit={(event) => void send(event)} className="flex items-end gap-2">
        <label htmlFor={composerId} className="sr-only">댓글 쓰기</label>
        <textarea id={composerId} value={draft} onChange={(event) => setDraft(event.target.value)} maxLength={LIMITS.comment} rows={1} placeholder="댓글을 남겨 보세요"
          className="min-w-0 flex-1 resize-none rounded-md border border-slate-700 bg-slate-950 px-2.5 py-2 text-sm text-slate-100 outline-none placeholder:text-slate-500 focus:border-teal-500" />
        <button type="submit" disabled={sending || draft.trim() === ''} aria-label="댓글 남기기"
          className="shrink-0 rounded-md bg-teal-500 p-2.5 text-slate-950 hover:bg-teal-400 disabled:opacity-60">
          <Send size={16} />
        </button>
      </form>
    </section>
  );
}
