import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PinComment } from '../api/types';
import CommentThread from './CommentThread';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const ME = 'me';
const NOW = new Date().toISOString();

function comment(id: string, userId: string, body: string, editedAt: string | null = null, extra: Partial<PinComment> = {}): PinComment {
  return {
    id, pinId: 'p', userId, authorNickname: userId === ME ? '나' : '친구', authorColor: '#14B8A6', body, createdAt: NOW, editedAt,
    parentId: null, replyToNickname: null, deleted: false, ...extra,
  };
}
const reply = (id: string, parentId: string, userId: string, body: string, replyToNickname: string | null = '친구', extra: Partial<PinComment> = {}) =>
  comment(id, userId, body, null, { parentId, replyToNickname, ...extra });
const tombstone = (id: string, extra: Partial<PinComment> = {}): PinComment =>
  comment(id, 'x', '', null, { userId: null, authorNickname: '', deleted: true, ...extra });

let container: HTMLDivElement;
let root: Root;
beforeEach(() => {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
});

interface Handlers {
  onAdd: (body: string, parentId?: string) => Promise<void>;
  onEdit: (id: string, body: string) => Promise<void>;
  onDelete: (id: string) => Promise<void>;
}

async function render(comments: PinComment[], overrides: Partial<Handlers> = {}, extra: { loading?: boolean; loadError?: string | null; canDelete?: (c: PinComment) => boolean; focusCommentId?: string | null } = {}) {
  const handlers: Handlers = { onAdd: vi.fn().mockResolvedValue(undefined), onEdit: vi.fn().mockResolvedValue(undefined), onDelete: vi.fn().mockResolvedValue(undefined), ...overrides };
  await act(async () => {
    root.render(
      <CommentThread comments={comments} loading={extra.loading ?? false} loadError={extra.loadError ?? null} currentUserId={ME}
        canDelete={extra.canDelete ?? ((c) => c.userId === ME)} focusCommentId={extra.focusCommentId ?? null} {...handlers} />,
    );
  });
  return handlers;
}

const button = (label: string) => container.querySelector<HTMLButtonElement>(`button[aria-label="${label}"]`);
const buttons = (label: string) => [...container.querySelectorAll<HTMLButtonElement>(`button[aria-label="${label}"]`)];

async function type(element: HTMLTextAreaElement, value: string) {
  await act(async () => {
    Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')?.set?.call(element, value);
    element.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

describe('댓글 목록', () => {
  it('없으면 첫 댓글을 권하고, 불러오는 중이면 그렇게 말한다', async () => {
    await render([]);
    expect(container.textContent).toContain('아직 댓글이 없어요');
    await render([], {}, { loading: true });
    expect(container.textContent).toContain('불러오는 중');
  });

  it('작성자, 본문, 수정됨 표시를 보여 준다', async () => {
    await render([comment('1', 'other', '웨이팅 길어요'), comment('2', ME, '평일에 갈게요', NOW)]);
    expect(container.textContent).toContain('웨이팅 길어요');
    expect(container.textContent).toContain('친구');
    expect(container.textContent).toContain('댓글 (2)');
    expect(container.textContent).toContain('수정됨');
    expect(container.textContent).toContain('방금');
  });

  it('고치기는 내 댓글에만, 지우기는 canDelete 가 허락한 댓글에만 보인다(핀을 꽂은 사람은 남의 댓글도 지운다)', async () => {
    await render([comment('1', 'other', '남의 댓글'), comment('2', ME, '내 댓글')], {}, { canDelete: () => true });
    expect(buttons('댓글 고치기')).toHaveLength(1);
    expect(buttons('댓글 지우기')).toHaveLength(2);

    await render([comment('1', 'other', '남의 댓글'), comment('2', ME, '내 댓글')]);
    expect(buttons('댓글 지우기')).toHaveLength(1);
  });

  it('불러오기 실패는 안내 문구로 보여 준다', async () => {
    await render([], {}, { loadError: '댓글을 불러오지 못했어요.' });
    expect(container.querySelector('[role="alert"]')?.textContent).toContain('불러오지 못했어요');
  });
});

describe('댓글 쓰기', () => {
  it('공백뿐이면 보낼 수 없고, 쓰면 앞뒤를 자른 본문으로 보내고 입력칸을 비운다', async () => {
    const handlers = await render([]);
    const input = container.querySelector<HTMLTextAreaElement>('textarea');
    expect(button('댓글 남기기')?.disabled).toBe(true);

    await type(input as HTMLTextAreaElement, '   ');
    expect(button('댓글 남기기')?.disabled).toBe(true);

    await type(input as HTMLTextAreaElement, '  여기 좋아요  ');
    await act(async () => button('댓글 남기기')?.click());

    expect(handlers.onAdd).toHaveBeenCalledWith('여기 좋아요', undefined);
    expect(container.querySelector<HTMLTextAreaElement>('textarea')?.value).toBe('');
  });

  it('보내지 못하면 입력한 글은 그대로 두고 오류를 알린다', async () => {
    await render([], { onAdd: vi.fn().mockRejectedValue(new Error('댓글은 핀마다 최대 500개까지 남길 수 있습니다.')) });
    const input = container.querySelector<HTMLTextAreaElement>('textarea');
    await type(input as HTMLTextAreaElement, '남기고 싶은 말');
    await act(async () => button('댓글 남기기')?.click());

    expect(container.querySelector('[role="alert"]')?.textContent).toContain('최대 500개');
    expect(container.querySelector<HTMLTextAreaElement>('textarea')?.value).toBe('남기고 싶은 말');
  });
});

describe('댓글 고치기와 지우기', () => {
  it('고치기: 본문을 바꿔 저장하면 onEdit 으로 보내고 편집을 닫는다', async () => {
    const handlers = await render([comment('2', ME, '처음')]);
    await act(async () => button('댓글 고치기')?.click());
    const editor = container.querySelector<HTMLTextAreaElement>('textarea');
    expect(editor?.value).toBe('처음');

    await type(editor as HTMLTextAreaElement, '  바꿈  ');
    const save = [...container.querySelectorAll('button')].find((candidate) => candidate.textContent?.includes('저장'));
    await act(async () => save?.click());

    expect(handlers.onEdit).toHaveBeenCalledWith('2', '바꿈');
    expect(button('댓글 고치기')).not.toBeNull();
  });

  it('지우기: onDelete 로 보내고, 실패하면 오류를 보여 준다', async () => {
    const handlers = await render([comment('2', ME, '지울 댓글')], { onDelete: vi.fn().mockRejectedValue(new Error('권한이 없어요')) });
    await act(async () => button('댓글 지우기')?.click());

    expect(handlers.onDelete).toHaveBeenCalledWith('2');
    expect(container.querySelector('[role="alert"]')?.textContent).toContain('권한이 없어요');
  });
});

describe('답글(스레드)', () => {
  it('답글은 속한 댓글 아래에 한 단계 들여서 묶이고, 누구에게 답한 글인지 @닉네임으로 보인다', async () => {
    await render([comment('r1', 'other', '원글 하나'), comment('r2', ME, '원글 둘'), reply('a1', 'r1', ME, '첫 답글', '친구'), reply('a2', 'r1', 'other', '둘째 답글', '나')]);

    const threads = [...container.querySelectorAll('[data-thread-id]')];
    expect(threads.map((t) => t.getAttribute('data-thread-id'))).toEqual(['r1', 'r2']);
    const firstReplies = [...(threads[0].querySelectorAll('ul[aria-label="답글"] [data-comment-id]'))].map((el) => el.getAttribute('data-comment-id'));
    expect(firstReplies).toEqual(['a1', 'a2']);
    expect(threads[1].querySelector('ul[aria-label="답글"]')).toBeNull();
    expect(threads[0].textContent).toContain('@친구');
    expect(threads[0].textContent).toContain('@나');
    expect(container.textContent).toContain('댓글 (4)');
  });

  it('답글 버튼을 누르면 입력칸이 그 사람에게 답하는 모드가 되고, 보내면 그 댓글의 id 로 답글을 남긴다', async () => {
    const handlers = await render([comment('r1', 'other', '질문이에요')]);
    await act(async () => button('친구님에게 답글')?.click());

    expect(container.textContent).toContain('↪ 친구님에게 답글');
    const input = container.querySelector<HTMLTextAreaElement>('textarea[placeholder]');
    expect(input?.placeholder).toContain('친구님에게 답글을 남겨 보세요');
    await type(input as HTMLTextAreaElement, '  답이에요  ');
    await act(async () => button('답글 남기기')?.click());

    expect(handlers.onAdd).toHaveBeenCalledWith('답이에요', 'r1');
    expect(container.textContent).not.toContain('↪');
    expect(container.querySelector<HTMLTextAreaElement>('textarea[placeholder]')?.placeholder).toBe('댓글을 남겨 보세요');
  });

  it('답글에 다시 답글을 달면 그 답글의 id 를 보낸다(서버가 같은 스레드에 붙인다)', async () => {
    const handlers = await render([comment('r1', 'other', '원글'), reply('a1', 'r1', 'other', '답글', '친구')]);
    const buttons = [...container.querySelectorAll<HTMLButtonElement>('button[aria-label="친구님에게 답글"]')];
    await act(async () => buttons[1].click());
    await type(container.querySelector<HTMLTextAreaElement>('textarea[placeholder]') as HTMLTextAreaElement, '답글에 답글');
    await act(async () => button('답글 남기기')?.click());
    expect(handlers.onAdd).toHaveBeenCalledWith('답글에 답글', 'a1');
  });

  it('답글 모드는 취소할 수 있고, 보내지 못하면 입력한 글과 답글 대상이 그대로 남는다', async () => {
    const handlers = await render([comment('r1', 'other', '원글')], { onAdd: vi.fn().mockRejectedValue(new Error('서버 오류')) });
    await act(async () => button('친구님에게 답글')?.click());
    await type(container.querySelector<HTMLTextAreaElement>('textarea[placeholder]') as HTMLTextAreaElement, '쓰던 글');
    await act(async () => button('답글 남기기')?.click());
    expect(handlers.onAdd).toHaveBeenCalledTimes(1);
    expect(container.querySelector('[role="alert"]')?.textContent).toContain('서버 오류');
    expect(container.textContent).toContain('↪ 친구님에게 답글');
    expect(container.querySelector<HTMLTextAreaElement>('textarea[placeholder]')?.value).toBe('쓰던 글');

    await act(async () => button('답글 취소')?.click());
    expect(container.textContent).not.toContain('↪');
  });

  it('삭제된 댓글은 안내 문구만 보이고(작성자·내용·버튼 없음) 그 아래 답글은 그대로 보이며, 댓글 수에서 빠진다', async () => {
    await render([tombstone('r1'), reply('a1', 'r1', 'other', '남아 있는 답글', null), comment('r2', 'other', '다른 댓글')]);

    const dead = container.querySelector('[data-comment-id="r1"]');
    expect(dead?.textContent).toBe('삭제된 댓글이에요');
    expect(dead?.querySelector('button')).toBeNull();
    expect(container.textContent).toContain('남아 있는 답글');
    expect(container.textContent).toContain('댓글 (2)');
  });

  it('답글을 달려던 댓글이 지워지면 답글 대상에서 뺀다', async () => {
    await render([comment('r1', 'other', '원글'), reply('a1', 'r1', 'other', '답글')]);
    await act(async () => button('친구님에게 답글')?.click());
    expect(container.textContent).toContain('↪');

    await act(async () => root.render(
      <CommentThread comments={[tombstone('r1'), reply('a1', 'r1', 'other', '답글')]} loading={false} loadError={null} currentUserId={ME}
        canDelete={() => false} onAdd={vi.fn()} onEdit={vi.fn()} onDelete={vi.fn()} />,
    ));
    expect(container.textContent).not.toContain('↪');
  });

  it('답글 버튼도 모바일에서 누르기 쉬운 크기(32px 이상)다', async () => {
    await render([comment('r1', 'other', '원글')]);
    expect(button('친구님에게 답글')?.className).toContain('min-h-8');
    expect(button('친구님에게 답글')?.className).toContain('min-w-8');
  });
});

describe('알림에서 들어왔을 때 그 댓글로 이동', () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); });
  const scrolled: Array<{ id: string | undefined; options: unknown }> = [];

  beforeEach(() => {
    scrolled.length = 0;
    Element.prototype.scrollIntoView = function scrollIntoView(this: Element, options?: unknown) {
      scrolled.push({ id: (this as HTMLElement).dataset.commentId, options });
    };
  });

  it('주어진 댓글(답글이어도)로 스크롤하고 잠깐 강조한 뒤 강조를 푼다', async () => {
    await render([comment('r1', 'other', '원글'), reply('a1', 'r1', 'other', '찾아갈 답글')], {}, { focusCommentId: 'a1' });

    expect(scrolled).toEqual([{ id: 'a1', options: { block: 'center' } }]);
    const target = container.querySelector('[data-comment-id="a1"]');
    expect(target?.className).toContain('ring-teal-400');
    expect(container.querySelector('[data-comment-id="r1"]')?.className).not.toContain('ring-teal-400');

    await act(async () => { vi.advanceTimersByTime(3000); });
    expect(container.querySelector('[data-comment-id="a1"]')?.className).not.toContain('ring-teal-400');
  });

  it('같은 댓글로는 한 번만 이동한다(목록이 다시 그려져도 계속 끌어당기지 않는다)', async () => {
    const list = [comment('r1', 'other', '원글'), reply('a1', 'r1', 'other', '답글')];
    await render(list, {}, { focusCommentId: 'a1' });
    await render([...list, reply('a2', 'r1', 'other', '새 답글')], {}, { focusCommentId: 'a1' });
    expect(scrolled).toHaveLength(1);
  });

  it('댓글이 아직 불러와지지 않았으면 기다렸다가, 나타나면 이동한다', async () => {
    await render([], {}, { focusCommentId: 'a1', loading: true });
    expect(scrolled).toHaveLength(0);
    await render([comment('r1', 'other', '원글'), reply('a1', 'r1', 'other', '답글')], {}, { focusCommentId: 'a1' });
    expect(scrolled.map((entry) => entry.id)).toEqual(['a1']);
  });

  it('댓글은 이미 채워졌지만 불러오기가 아직 끝나지 않은 동안은 기다렸다가, 끝나서 목록이 그려지면 이동한다', async () => {
    const list = [comment('r1', 'other', '원글'), reply('a1', 'r1', 'other', '답글')];
    await render(list, {}, { focusCommentId: 'a1', loading: true });   // 목록 데이터는 있지만 "불러오는 중" 만 그려진 상태
    expect(scrolled).toHaveLength(0);

    await render(list, {}, { focusCommentId: 'a1', loading: false });  // 같은 목록으로 loading 만 풀린다

    expect(scrolled.map((entry) => entry.id)).toEqual(['a1']);
  });

  it('없는 댓글을 가리키면(지워졌으면) 아무것도 하지 않는다', async () => {
    await render([comment('r1', 'other', '원글')], {}, { focusCommentId: 'gone' });
    expect(scrolled).toHaveLength(0);
  });
});
