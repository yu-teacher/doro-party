import { describe, expect, it } from 'vitest';
import { invitePath, inviteUrl } from './inviteLink';

describe('초대 링크 주소', () => {
  it('친구 초대와 모임 초대는 경로가 다르다', () => {
    expect(invitePath('friend', 'abc')).toBe('/invite/abc');
    expect(invitePath('group', 'abc')).toBe('/join/abc');
  });

  it('코드의 특수문자를 인코딩한다', () => {
    expect(invitePath('friend', 'a/b?c')).toBe('/invite/a%2Fb%3Fc');
  });

  it('전체 주소는 도메인과 마운트 경로(/party)를 포함한다', () => {
    expect(inviteUrl('https://example.test', 'group', 'XyZ-_1')).toMatch(/^https:\/\/example\.test.*\/join\/XyZ-_1$/);
  });
});
