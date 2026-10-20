import { createRoot } from 'react-dom/client';
import type { Root } from 'react-dom/client';
import { act, useState } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LIMITS } from '../api/types';
import PhotoPicker from './PhotoPicker';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

let container: HTMLDivElement;
let root: Root;
let created: string[];
let revoked: string[];
let latest: File[];

const file = (name: string) => new File([name], name, { type: 'image/jpeg', lastModified: 1 });

function Harness({ disabled = false, initial = [] }: { disabled?: boolean; initial?: File[] }) {
  const [files, setFiles] = useState<File[]>(initial);
  latest = files;
  return <PhotoPicker files={files} onChange={setFiles} disabled={disabled} />;
}

beforeEach(() => {
  created = [];
  revoked = [];
  let n = 0;
  vi.stubGlobal('URL', Object.assign(URL, {
    createObjectURL: () => { const url = `blob:test/${++n}`; created.push(url); return url; },
    revokeObjectURL: (url: string) => { revoked.push(url); },
  }));
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});
afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.unstubAllGlobals();
});

const input = () => container.querySelector<HTMLInputElement>('input[type="file"]') as HTMLInputElement;
const render = (props: { disabled?: boolean; initial?: File[] } = {}) => act(async () => root.render(<Harness {...props} />));
async function pick(...files: File[]) {
  Object.defineProperty(input(), 'files', { configurable: true, value: files });
  await act(async () => { input().dispatchEvent(new Event('change', { bubbles: true })); });
}

describe('사진 고르기', () => {
  it('처음에는 사진 추가 칸만 있고, 파일 선택은 사진 여러 장을 받는다', async () => {
    await render();
    expect(container.textContent).toContain('사진 추가');
    expect(container.querySelectorAll('img')).toHaveLength(0);
    expect(input().accept).toBe('image/*');
    expect(input().multiple).toBe(true);
  });

  it('고른 사진을 썸네일로 보여 주고 장수를 센다', async () => {
    await render();
    await pick(file('a.jpg'), file('b.jpg'));
    expect(container.querySelectorAll('img')).toHaveLength(2);
    expect(container.textContent).toContain(`2/${LIMITS.photosPerPin}장`);
    expect(latest.map((f) => f.name)).toEqual(['a.jpg', 'b.jpg']);
  });

  it('이어서 더 고르면 앞의 사진 뒤에 붙는다', async () => {
    await render();
    await pick(file('a.jpg'));
    await pick(file('b.jpg'));
    expect(latest.map((f) => f.name)).toEqual(['a.jpg', 'b.jpg']);
  });

  it('하나씩 뺄 수 있다', async () => {
    await render();
    await pick(file('a.jpg'), file('b.jpg'), file('c.jpg'));
    await act(async () => container.querySelector<HTMLButtonElement>('button[aria-label="사진 2 빼기"]')?.click());
    expect(latest.map((f) => f.name)).toEqual(['a.jpg', 'c.jpg']);
    expect(container.querySelectorAll('img')).toHaveLength(2);
  });

  it(`최대 ${LIMITS.photosPerPin}장까지만 받고, 넘으면 안내하며, 가득 차면 추가 칸이 사라진다`, async () => {
    await render();
    await pick(...Array.from({ length: LIMITS.photosPerPin + 3 }, (_, i) => file(`p${i}.jpg`)));
    expect(latest).toHaveLength(LIMITS.photosPerPin);
    expect(container.querySelector('[role="status"]')?.textContent).toContain(`${LIMITS.photosPerPin}장까지`);
    expect(container.textContent).not.toContain('사진 추가');
    await act(async () => container.querySelector<HTMLButtonElement>('button[aria-label="사진 1 빼기"]')?.click());
    expect(container.textContent).toContain('사진 추가');
  });

  it('같은 사진을 다시 골라도 선택 창이 그대로 동작한다(선택값을 비운다)', async () => {
    await render();
    await pick(file('a.jpg'));
    expect(input().value).toBe('');
  });

  it('저장 중에는 추가·빼기가 막힌다', async () => {
    await render({ disabled: true, initial: [file('a.jpg')] });
    expect(container.querySelector<HTMLButtonElement>('button[aria-label="사진 1 빼기"]')?.disabled).toBe(true);
    expect([...container.querySelectorAll('button')].find((b) => b.textContent?.includes('사진 추가'))?.disabled).toBe(true);
  });

  it('미리보기 주소는 사진이 바뀌거나 화면이 닫히면 해제한다(메모리 누수 방지)', async () => {
    await render();
    await pick(file('a.jpg'), file('b.jpg'));
    const firstRound = [...created];
    await act(async () => container.querySelector<HTMLButtonElement>('button[aria-label="사진 1 빼기"]')?.click());
    expect(revoked).toEqual(expect.arrayContaining(firstRound.slice(0, 2)));

    const before = revoked.length;
    act(() => root.unmount());
    expect(revoked.length).toBeGreaterThan(before);
    root = createRoot(container);
  });
});
