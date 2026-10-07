/** 텍스트를 클립보드에 복사한다. 권한이 없거나 지원하지 않으면 false. */
export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch (error) {
    console.warn('Could not copy to the clipboard', error);
    return false;
  }
}

export type ShareOutcome = 'shared' | 'copied' | 'cancelled' | 'failed';

/**
 * 링크를 보낸다. 휴대폰처럼 공유 시트(카톡 등)를 열 수 있으면 그걸 쓰고, 아니면 복사한다.
 * 사용자가 공유 시트를 닫은 것은 오류가 아니라 cancelled 다.
 */
export async function shareLink(url: string, title: string, text: string): Promise<ShareOutcome> {
  if (typeof navigator.share === 'function') {
    try {
      await navigator.share({ title, text, url });
      return 'shared';
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        return 'cancelled';
      }
      console.warn('Native share failed, falling back to copy', error);
    }
  }
  return (await copyText(url)) ? 'copied' : 'failed';
}
