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
