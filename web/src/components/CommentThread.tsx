import { Check, CornerDownRight, Pencil, Send, Trash2, X } from 'lucide-react';
import { useEffect, useId, useMemo, useRef, useState } from 'react';
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
  /** parentId 가 있으면 그 댓글에 대한 답글이다 */
  onAdd: (body: string, parentId?: string) => Promise<void>;
  onEdit: (commentId: string, body: string) => Promise<void>;
  onDelete: (commentId: string) => Promise<void>;
  /** 알림을 눌러 들어왔을 때 보여 줄 댓글: 불러오면 그 댓글로 스크롤하고 잠깐 강조한다 */
  focusCommentId?: string | null;
}

/** 강조 표시를 유지하는 시간(ms) */
const HIGHLIGHT_MS = 2500;
const TOMBSTONE_TEXT = '삭제된 댓글이에요';

interface Thread {
  root: PinComment;
  replies: PinComment[];
}

/** 맨 위 댓글 아래에 그 답글을 묶는다(둘 다 오래된 순). 서버가 맨 위 댓글이 없는 답글은 보내지 않지만, 혹시 있으면 버리지 않고 맨 끝에 둔다. */
function groupThreads(comments: PinComment[]): Thread[] {
  const threads = new Map<string, Thread>();
  const orphans: PinComment[] = [];
  for (const comment of comments) {
    if (comment.parentId === null) {
      threads.set(comment.id, { root: comment, replies: [] });
    }
  }
  for (const comment of comments) {
    if (comment.parentId !== null) {
      const thread = threads.get(comment.parentId);
      if (thread) {
        thread.replies.push(comment);
      } else {
        orphans.push(comment);
      }
    }
  }
  return [...threads.values(), ...orphans.map((orphan) => ({ root: orphan, replies: [] }))];
}

/** 핀에 남긴 댓글과 답글. 지도를 볼 수 있는 사람이면 누구나 쓰고, 고치는 것은 쓴 사람만 한다. */
export default function CommentThread({ comments, loading, loadError, currentUserId, canDelete, onAdd, onEdit, onDelete, focusCommentId = null }: Props) {
  const composerId = useId();
  const composer = useRef<HTMLTextAreaElement>(null);
  const section = useRef<HTMLElement>(null);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editDraft, setEditDraft] = useState('');
  const [replyTo, setReplyTo] = useState<PinComment | null>(null);
  const [highlightedId, setHighlightedId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const focused = useRef<string | null>(null);
  const threads = useMemo(() => groupThreads(comments), [comments]);
  const visibleCount = comments.filter((comment) => !comment.deleted).length;

  // 알림으로 들어왔으면 그 댓글을 화면 가운데로 가져오고 잠깐 강조한다(한 번만). 댓글은 먼저 채워지고 화면은 불러오기가 끝난 뒤에 그려지므로 loading 도 본다.
  useEffect(() => {
    if (focusCommentId === null || focused.current === focusCommentId) {
      return undefined;
    }
    const element = [...(section.current?.querySelectorAll<HTMLElement>('[data-comment-id]') ?? [])].find((candidate) => candidate.dataset.commentId === focusCommentId);
    if (!element) {
      return undefined;
    }
    focused.current = focusCommentId;
    element.scrollIntoView({ block: 'center' });
    setHighlightedId(focusCommentId);
    const timer = window.setTimeout(() => setHighlightedId(null), HIGHLIGHT_MS);
    return () => window.clearTimeout(timer);
  }, [focusCommentId, comments, loading]);

  // 답글을 달려던 댓글이 지워졌으면 답글 대상에서 뺀다
  useEffect(() => {
    if (replyTo !== null && !comments.some((comment) => comment.id === replyTo.id && !comment.deleted)) {
      setReplyTo(null);
    }
  }, [comments, replyTo]);

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
    if (await run(() => onAdd(body, replyTo?.id), '댓글을 남기지 못했어요.')) {
      setDraft('');
      setReplyTo(null);
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

  const startReply = (comment: PinComment) => {
    setReplyTo(comment);
    setError(null);
    composer.current?.focus();
  };

  const renderComment = (comment: PinComment, isReply: boolean) => {
    if (comment.deleted) {
      return (
        <li key={comment.id} data-comment-id={comment.id} className="text-xs italic text-slate-500">{TOMBSTONE_TEXT}</li>
      );
    }
    const mine = comment.userId === currentUserId;
    const editing = editingId === comment.id;
    return (
      <li key={comment.id} data-comment-id={comment.id}
        className={`flex flex-col gap-1 rounded-lg transition-colors ${highlightedId === comment.id ? 'bg-teal-400/15 ring-1 ring-teal-400/60' : ''}`}>
        <div className="flex items-center justify-between gap-2 text-xs text-slate-500">
          <span className="flex min-w-0 items-center gap-2">
            <UserDot nickname={comment.authorNickname} color={comment.authorColor} />
            <span className="shrink-0">{formatRelative(comment.createdAt)}{comment.editedAt && ' · 수정됨'}</span>
          </span>
          {!editing && (
            <span className="flex shrink-0 items-center">
              <button type="button" onClick={() => startReply(comment)} aria-label={`${comment.authorNickname}님에게 답글`}
                className="flex min-h-8 min-w-8 items-center justify-center rounded text-slate-500 hover:bg-slate-800 hover:text-teal-300">
                <CornerDownRight size={14} />
              </button>
              {mine && (
                <button type="button" onClick={() => { setEditingId(comment.id); setEditDraft(comment.body); setError(null); }}
                  className="flex min-h-8 min-w-8 items-center justify-center rounded text-slate-500 hover:bg-slate-800 hover:text-slate-200" aria-label="댓글 고치기">
                  <Pencil size={14} />
                </button>
              )}
              {canDelete(comment) && (
                <button type="button" onClick={() => void run(() => onDelete(comment.id), '댓글을 지우지 못했어요.')}
                  className="flex min-h-8 min-w-8 items-center justify-center rounded text-slate-500 hover:bg-slate-800 hover:text-rose-300" aria-label="댓글 지우기">
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
          <p className="whitespace-pre-wrap break-words text-sm leading-relaxed text-slate-200">
            {isReply && comment.replyToNickname && <span className="mr-1 font-medium text-teal-300">@{comment.replyToNickname}</span>}
            {comment.body}
          </p>
        )}
      </li>
    );
  };

  return (
    <section ref={section} aria-label="댓글">
      <h3 className="mb-2 text-sm font-semibold text-slate-200">댓글 {visibleCount > 0 && <span className="text-slate-500">({visibleCount})</span>}</h3>

      {loadError && <p role="alert" className="mb-2 rounded-lg bg-rose-500/15 px-3 py-2 text-xs text-rose-200">{loadError}</p>}
      {error && <p role="alert" className="mb-2 rounded-lg bg-rose-500/15 px-3 py-2 text-xs text-rose-200">{error}</p>}

      {loading ? (
        <p className="text-xs text-slate-500">댓글을 불러오는 중…</p>
      ) : threads.length === 0 ? (
        <p className="mb-2 text-xs text-slate-500">아직 댓글이 없어요. 이 장소에 대해 한마디 남겨 보세요.</p>
      ) : (
        <ul className="mb-3 flex flex-col gap-3">
          {threads.map(({ root, replies }) => (
            <li key={root.id} className="flex flex-col gap-2" data-thread-id={root.id}>
              <ul className="flex flex-col">{renderComment(root, false)}</ul>
              {replies.length > 0 && (
                <ul className="ml-1 flex flex-col gap-2.5 border-l border-slate-700 pl-3" aria-label="답글">
                  {replies.map((reply) => renderComment(reply, true))}
                </ul>
              )}
            </li>
          ))}
        </ul>
      )}

      <form onSubmit={(event) => void send(event)} className="flex flex-col gap-1.5">
        {replyTo && (
          <div className="flex items-center justify-between gap-2 rounded-md bg-teal-500/10 px-2.5 py-1.5 text-xs text-teal-200">
            <span className="min-w-0 truncate">↪ {replyTo.authorNickname}님에게 답글</span>
            <button type="button" onClick={() => setReplyTo(null)} aria-label="답글 취소" className="flex min-h-8 min-w-8 shrink-0 items-center justify-center rounded text-teal-300 hover:bg-teal-500/20">
              <X size={14} />
            </button>
          </div>
        )}
        <div className="flex items-end gap-2">
          <label htmlFor={composerId} className="sr-only">{replyTo ? '답글 쓰기' : '댓글 쓰기'}</label>
          <textarea ref={composer} id={composerId} value={draft} onChange={(event) => setDraft(event.target.value)} maxLength={LIMITS.comment} rows={1}
            placeholder={replyTo ? `${replyTo.authorNickname}님에게 답글을 남겨 보세요` : '댓글을 남겨 보세요'}
            className="min-w-0 flex-1 resize-none rounded-md border border-slate-700 bg-slate-950 px-2.5 py-2 text-sm text-slate-100 outline-none placeholder:text-slate-500 focus:border-teal-500" />
          <button type="submit" disabled={sending || draft.trim() === ''} aria-label={replyTo ? '답글 남기기' : '댓글 남기기'}
            className="shrink-0 rounded-md bg-teal-500 p-2.5 text-slate-950 hover:bg-teal-400 disabled:opacity-60">
            <Send size={16} />
          </button>
        </div>
      </form>
    </section>
  );
}
