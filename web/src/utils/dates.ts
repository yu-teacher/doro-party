/** 서버가 "오늘" 을 정하는 시간대(백엔드 party.timezone 과 같다). 방문 날짜가 미래인지 판단할 때 서버와 같은 기준을 쓴다. */
export const SERVICE_TIMEZONE = 'Asia/Seoul';

/** 서비스 시간대 기준 오늘(yyyy-MM-dd). */
export function todayInServiceZone(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: SERVICE_TIMEZONE, year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);
}

/** yyyy-MM-dd 를 "2026년 10월 7일" 로. 시간대에 흔들리지 않게 문자열을 직접 나눈다. 형식이 다르면 그대로 돌려준다. */
export function formatDay(isoDate: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(isoDate);
  return match ? `${match[1]}년 ${Number(match[2])}월 ${Number(match[3])}일` : isoDate;
}

const MINUTE_MS = 60_000;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;
const RELATIVE_DAYS_MAX = 7;

/** 댓글 시각: 방금·N분 전·N시간 전·N일 전, 일주일이 넘으면 날짜. 형식이 잘못됐으면 빈 문자열. */
export function formatRelative(iso: string, now: Date = new Date()): string {
  const time = new Date(iso).getTime();
  if (Number.isNaN(time)) {
    return '';
  }
  const elapsed = Math.max(0, now.getTime() - time);
  if (elapsed < MINUTE_MS) {
    return '방금';
  }
  if (elapsed < HOUR_MS) {
    return `${Math.floor(elapsed / MINUTE_MS)}분 전`;
  }
  if (elapsed < DAY_MS) {
    return `${Math.floor(elapsed / HOUR_MS)}시간 전`;
  }
  if (elapsed < RELATIVE_DAYS_MAX * DAY_MS) {
    return `${Math.floor(elapsed / DAY_MS)}일 전`;
  }
  return formatDay(todayInServiceZone(new Date(time)));
}
