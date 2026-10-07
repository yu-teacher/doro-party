import axios from 'axios';
import { API_BASE } from '../config';
import { CSRF_HEADER, CSRF_VALUE } from './csrf';

/** 로그인은 서버가 관리하는 세션 쿠키(HttpOnly)로 이루어진다. 같은 사이트 요청에는 브라우저가 쿠키를 알아서 붙인다. */
export const apiClient = axios.create({
  baseURL: API_BASE,
  headers: {
    'Content-Type': 'application/json',
    [CSRF_HEADER]: CSRF_VALUE,
  },
});

interface ErrorBody {
  message?: string;
  error?: { message?: string };
}

/** 취소(AbortController)는 그대로 전달해 호출자가 구분할 수 있게 하고, 나머지는 서버의 표준 오류 메시지로 바꾼다. */
apiClient.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isCancel(error)) {
      return Promise.reject(error);
    }
    if (axios.isAxiosError<ErrorBody>(error)) {
      const body = error.response?.data;
      const message = body?.message || body?.error?.message || error.message || '요청 처리 중 오류가 발생했습니다.';
      return Promise.reject(new Error(message));
    }
    return Promise.reject(error instanceof Error ? error : new Error('요청 처리 중 오류가 발생했습니다.'));
  },
);
