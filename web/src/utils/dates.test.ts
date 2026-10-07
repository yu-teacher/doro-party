import { describe, expect, it } from 'vitest';
import { formatDay, todayInServiceZone } from './dates';

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
