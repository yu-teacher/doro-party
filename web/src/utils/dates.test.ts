import { describe, expect, it } from 'vitest';
import { formatDay, formatRelative, todayInServiceZone } from './dates';

describe('todayInServiceZone', () => {
  it('UTC 로는 아직 어제인 시각도 서울 기준 오늘로 계산한다', () => {
    // 2026-10-06 16:30 UTC == 2026-10-07 01:30 KST
    expect(todayInServiceZone(new Date('2026-10-06T16:30:00Z'))).toBe('2026-10-07');
    expect(todayInServiceZone(new Date('2026-10-07T14:59:00Z'))).toBe('2026-10-07');
    expect(todayInServiceZone(new Date('2026-10-07T15:00:00Z'))).toBe('2026-10-08');
  });
});

describe('formatDay', () => {
  it('날짜를 한국어로 보여 준다', () => {
    expect(formatDay('2026-10-07')).toBe('2026년 10월 7일');
    expect(formatDay('2026-01-05')).toBe('2026년 1월 5일');
  });

  it('알 수 없는 형식은 그대로', () => {
    expect(formatDay('어제')).toBe('어제');
  });
});

describe('formatRelative', () => {
  const now = new Date('2026-10-09T12:00:00Z');
  const ago = (ms: number) => new Date(now.getTime() - ms).toISOString();

  it('방금, 분, 시간, 일 단위로 말한다', () => {
    expect(formatRelative(ago(30_000), now)).toBe('방금');
    expect(formatRelative(ago(5 * 60_000), now)).toBe('5분 전');
    expect(formatRelative(ago(3 * 3_600_000), now)).toBe('3시간 전');
    expect(formatRelative(ago(2 * 86_400_000), now)).toBe('2일 전');
  });

  it('일주일이 넘으면 날짜로 보여 준다', () => {
    expect(formatRelative('2026-09-01T03:00:00Z', now)).toBe('2026년 9월 1일');
  });

  it('시계가 어긋나 미래 시각이 와도 음수로 말하지 않고, 형식이 잘못되면 빈 문자열', () => {
    expect(formatRelative(new Date(now.getTime() + 60_000).toISOString(), now)).toBe('방금');
    expect(formatRelative('not-a-date', now)).toBe('');
  });
});
