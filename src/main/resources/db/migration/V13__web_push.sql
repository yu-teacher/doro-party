-- DORO Party V13: 웹 푸시
-- 구독은 기기(브라우저)마다 하나. endpoint 는 서버가 대신 접속하는 푸시 서비스 주소라서 비밀로 다루고 로그에 남기지 않는다.

CREATE TABLE push_subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES party_users(id) ON DELETE CASCADE,
    endpoint VARCHAR(2048) NOT NULL UNIQUE,
    p256dh VARCHAR(128) NOT NULL,
    auth VARCHAR(64) NOT NULL,
    user_agent VARCHAR(300),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_success_at TIMESTAMP WITH TIME ZONE,
    failure_count INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_push_subscriptions_user ON push_subscriptions(user_id, created_at);

-- 알림 종류별 켜기/끄기. 행이 없으면 모두 켜진 것으로 본다.
CREATE TABLE push_preferences (
    user_id UUID PRIMARY KEY REFERENCES party_users(id) ON DELETE CASCADE,
    comments BOOLEAN NOT NULL DEFAULT TRUE,
    friend_requests BOOLEAN NOT NULL DEFAULT TRUE,
    shares BOOLEAN NOT NULL DEFAULT TRUE
);
