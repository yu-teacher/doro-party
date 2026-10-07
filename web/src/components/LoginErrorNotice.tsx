import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { LOGIN_ERROR_MESSAGES, readLoginError, withoutLoginError, type LoginErrorKind } from '../utils/loginError';

/** 서버가 로그인 실패로 돌려보낸 쿼리(login_error)를 안내 문구로 보여 주고, 주소에서는 지운다. */
export default function LoginErrorNotice() {
  const { pathname, search } = useLocation();
  const navigate = useNavigate();
  const [kind, setKind] = useState<LoginErrorKind | null>(null);

  useEffect(() => {
    const found = readLoginError(search);
    if (found) {
      setKind(found);
      navigate({ pathname, search: withoutLoginError(search) }, { replace: true });
    }
  }, [search, pathname, navigate]);

  if (!kind) {
    return null;
  }
  return (
    <div role="alert" className="flex items-center justify-between gap-3 bg-rose-500/15 px-4 py-2 text-sm text-rose-200">
      <span>{LOGIN_ERROR_MESSAGES[kind]}</span>
      <button type="button" onClick={() => setKind(null)} className="font-semibold underline">
        닫기
      </button>
    </div>
  );
}
