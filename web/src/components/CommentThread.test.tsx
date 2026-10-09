import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PinComment } from '../api/types';
import CommentThread from './CommentThread';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const ME = 'me';
const NOW = new Date().toISOString();

function comment(id: string, userId: string, body: string, editedAt: string | null = null): PinComment {
  return { id, pinId: 'p', userId, authorNickname: userId === ME ? '나' : '친구', authorColor: '#14B8A6', body, createdAt: NOW, editedAt };
}

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
  onAdd: (body: string) => Promise<void>;
  onEdit: (id: string, body: string) => Promise<void>;
  onDelete: (id: string) => Promise<void>;
}

async function render(comments: PinComment[], overrides: Partial<Handlers> = {}, extra: { loading?: boolean; loadError?: string | null; canDelete?: (c: PinComment) => boolean } = {}) {
  const handlers: Handlers = { onAdd: vi.fn().mockResolvedValue(undefined), onEdit: vi.fn().mockResolvedValue(undefined), onDelete: vi.fn().mockResolvedValue(undefined), ...overrides };
  await act(async () => {
    root.render(
      <CommentThread comments={comments} loading={extra.loading ?? false} loadError={extra.loadError ?? null} currentUserId={ME}
        canDelete={extra.canDelete ?? ((c) => c.userId === ME)} {...handlers} />,
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

    expect(handlers.onAdd).toHaveBeenCalledWith('여기 좋아요');
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
