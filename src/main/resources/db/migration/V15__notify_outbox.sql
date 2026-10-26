-- DORO Party V15: 플랫폼 알림 서비스(doro-notify)로 보낼 알림 아웃박스. CENTRAL 모드에서 알림 서비스가 느리거나 잠깐 죽어 있어도 알림을 잃지 않게
-- 여기에 쌓아 두고 주기적으로 다시 보낸다. 보낸 행은 지운다(내용은 보내기 전까지만 남는다). 알림 서비스의 멱등 키 덕분에 다시 보내도 두 번 가지 않는다.
CREATE TABLE IF NOT EXISTS notify_outbox (
    id UUID PRIMARY KEY,
    recipient_id UUID NOT NULL,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(300) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    tag VARCHAR(200) NOT NULL,
    url VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_notify_outbox_next_attempt ON notify_outbox (next_attempt_at);
