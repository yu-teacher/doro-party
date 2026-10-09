import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

// CSS 를 ?raw 로 가져오면 vitest 가 빈 문자열을 주고, jsdom 환경에서는 import.meta.url 이 file: 이 아니라서
// 테스트를 실행하는 작업 디렉터리(web/)를 기준으로 파일을 직접 읽는다.
const css = readFileSync(resolve(process.cwd(), 'src/index.css'), 'utf8');

/** 터치 화면용 전역 규칙이 지워지지 않게 지키는 테스트(모바일에서 입력창 확대·작은 버튼을 막는 규칙). */
describe('터치 화면 전역 스타일', () => {
  const touchBlock = css.slice(css.indexOf('@media (pointer: coarse)'));

  it('터치 화면에서만 적용된다(마우스를 쓰는 화면은 그대로)', () => {
    expect(css).toContain('@media (pointer: coarse)');
    // 규칙은 모두 이 미디어 쿼리 안에 있어야 한다: 그 앞쪽 전역 영역에는 입력창 글자 크기를 강제하지 않는다
    const before = css.slice(0, css.indexOf('@media (pointer: coarse)'));
    expect(before).not.toMatch(/min-height:\s*36px/);
    expect(before).not.toMatch(/font-size:\s*16px/);
  });

  it('입력창 글자를 16px 로 둬 iOS Safari 가 입력할 때 화면을 확대하지 않게 한다', () => {
    expect(touchBlock).toMatch(/textarea[\s\S]*font-size:\s*16px/);
  });

  it('버튼은 최소 36px 높이를 갖되 스위치와 태그 지우기 버튼은 제외한다', () => {
    expect(touchBlock).toMatch(/min-height:\s*36px/);
    expect(touchBlock).toContain("[role='switch']");
    expect(touchBlock).toContain("[aria-label$='태그 지우기']");
  });
});
