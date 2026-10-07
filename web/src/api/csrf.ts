/**
 * 쿠키 인증으로 상태를 바꾸는 요청에 붙이는 CSRF 증명 헤더. 다른 사이트의 페이지는 CORS 허용 없이 커스텀 헤더를 붙일 수 없으므로
 * 이 헤더가 있다는 것은 이 사이트의 스크립트가 보낸 요청이라는 뜻이다. 서버(BffSessionAuthFilter)가 같은 이름을 검사한다.
 */
export const CSRF_HEADER = 'X-Party-Csrf';
export const CSRF_VALUE = '1';
