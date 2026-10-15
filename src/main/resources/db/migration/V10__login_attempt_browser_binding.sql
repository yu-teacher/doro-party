-- 로그인 CSRF 방지: 로그인을 시작한 브라우저에 심은 일회용 값의 해시를 로그인 시도에 묶어 두고, 콜백에서 그 브라우저인지 대조한다.
-- 이미 진행 중이던 시도(최대 10분 분량)는 값이 없으므로 콜백이 거부된다. 사용자는 로그인을 다시 시작하면 된다.
ALTER TABLE login_attempts ADD COLUMN browser_hash VARCHAR(64);
