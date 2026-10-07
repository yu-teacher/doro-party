/** 서버가 로그인 실패 시 돌려보내는 쿼리 값(login_error). 상세 사유는 노출하지 않는다. */
export type LoginErrorKind = 'cancelled' | 'failed';

const PARAM = 'login_error';

export const LOGIN_ERROR_MESSAGES: Record<LoginErrorKind, string> = {
  cancelled: '로그인이 취소되었어요.',
  failed: '로그인하지 못했어요. 잠시 후 다시 시도해 주세요.',
};

export function readLoginError(search: string): LoginErrorKind | null {
  const value = new URLSearchParams(search).get(PARAM);
  return value === 'cancelled' || value === 'failed' ? value : null;
}

/** login_error 만 지운 쿼리 문자열(앞의 ? 포함, 비면 빈 문자열). */
export function withoutLoginError(search: string): string {
  const params = new URLSearchParams(search);
  params.delete(PARAM);
  const rest = params.toString();
  return rest ? `?${rest}` : '';
}
