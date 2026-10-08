import { describe, expect, it } from 'vitest';
import type { ShareView } from '../api/types';
import { editorLabels, hasOtherEditors, summarizeEditors } from './editorSummary';

const share = (nickname: string, role: ShareView['role']): Pick<ShareView, 'role' | 'user'> => ({
  role,
  user: { id: nickname, username: nickname, nickname, color: '#fff' },
});

describe('summarizeEditors', () => {
  it('직접 편집 권한을 받은 친구만 모으고 이름순으로 정렬한다(보기만 받은 친구는 뺀다)', () => {
    const summary = summarizeEditors([share('지호', 'EDITOR'), share('민준', 'VIEWER'), share('서연', 'EDITOR')], 'NONE');
    expect(summary).toEqual({ names: ['서연', '지호'], everyFriend: false });
  });

  it('친구 전체 편집 공개는 별도로 표시하고, 보기 공개는 편집이 아니다', () => {
    expect(summarizeEditors([], 'EDITOR').everyFriend).toBe(true);
    expect(summarizeEditors([], 'VIEWER').everyFriend).toBe(false);
    expect(summarizeEditors([], 'NONE').everyFriend).toBe(false);
  });
});

describe('hasOtherEditors / editorLabels', () => {
  it('아무도 없으면 나만', () => {
    const summary = summarizeEditors([share('민준', 'VIEWER')], 'VIEWER');
    expect(hasOtherEditors(summary)).toBe(false);
    expect(editorLabels(summary)).toEqual(['나']);
  });

  it('직접 편집 권한자와 친구 전체 편집을 함께 보여 준다', () => {
    const summary = summarizeEditors([share('서연', 'EDITOR')], 'EDITOR');
    expect(hasOtherEditors(summary)).toBe(true);
    expect(editorLabels(summary)).toEqual(['나', '서연', '내 친구 전체']);
  });

  it('직접 권한자만 있어도 다른 편집자가 있는 것이다', () => {
    expect(hasOtherEditors(summarizeEditors([share('서연', 'EDITOR')], 'NONE'))).toBe(true);
  });
});
