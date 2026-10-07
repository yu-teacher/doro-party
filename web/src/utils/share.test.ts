import { afterEach, describe, expect, it, vi } from 'vitest';
import { copyText, shareLink } from './share';

const original = { share: navigator.share, clipboard: Object.getOwnPropertyDescriptor(navigator, 'clipboard') };

afterEach(() => {
  vi.restoreAllMocks();
  Object.defineProperty(navigator, 'share', { value: original.share, configurable: true });
  if (original.clipboard) {
    Object.defineProperty(navigator, 'clipboard', original.clipboard);
  } else {
    Reflect.deleteProperty(navigator, 'clipboard');
  }
});

function stubClipboard(writeText: (text: string) => Promise<void>) {
  Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
}

describe('copyText', () => {
  it('복사에 성공하면 true, 실패하면 false', async () => {
    stubClipboard(() => Promise.resolve());
    expect(await copyText('x')).toBe(true);

    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    stubClipboard(() => Promise.reject(new Error('denied')));
    expect(await copyText('x')).toBe(false);
  });
});

describe('shareLink', () => {
  it('공유 시트를 열 수 있으면 그걸 쓴다', async () => {
    const share = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'share', { value: share, configurable: true });

    expect(await shareLink('https://a.test/x', '제목', '문구')).toBe('shared');
    expect(share).toHaveBeenCalledWith({ title: '제목', text: '문구', url: 'https://a.test/x' });
  });

  it('사용자가 공유 시트를 닫은 것은 오류가 아니라 취소다', async () => {
    Object.defineProperty(navigator, 'share', { value: vi.fn().mockRejectedValue(new DOMException('closed', 'AbortError')), configurable: true });

    expect(await shareLink('https://a.test/x', 't', 't')).toBe('cancelled');
  });

  it('공유 시트가 없으면 복사한다', async () => {
    Object.defineProperty(navigator, 'share', { value: undefined, configurable: true });
    const writeText = vi.fn().mockResolvedValue(undefined);
    stubClipboard(writeText);

    expect(await shareLink('https://a.test/x', 't', 't')).toBe('copied');
    expect(writeText).toHaveBeenCalledWith('https://a.test/x');
  });

  it('공유도 복사도 못 하면 failed', async () => {
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    Object.defineProperty(navigator, 'share', { value: undefined, configurable: true });
    stubClipboard(() => Promise.reject(new Error('denied')));

    expect(await shareLink('https://a.test/x', 't', 't')).toBe('failed');
  });
});
