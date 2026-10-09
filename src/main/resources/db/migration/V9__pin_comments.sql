-- DORO Party V9: 핀 댓글
-- 지도를 볼 수 있는 사람(viewer 이상)이 핀에 댓글을 남긴다. 접근 권한은 Guard 가 판정하고 DB 에는 두지 않는다.

CREATE TABLE pin_comments (
    id UUID PRIMARY KEY,
    pin_id UUID NOT NULL REFERENCES pins(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES party_users(id) ON DELETE CASCADE,
    body VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    edited_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_pin_comments_pin ON pin_comments(pin_id, created_at);
CREATE INDEX idx_pin_comments_user ON pin_comments(user_id);

-- 읽음 표시: 핀과 사용자 한 쌍에 하나. 이 시각까지의 댓글을 읽었다는 뜻이다.
-- 핀을 만든 사람과 그 핀에 댓글을 남긴 사람에게만 "새 댓글" 로 알려 준다(지도를 볼 수 있다고 모든 핀의 알림을 받게 하지 않는다).
CREATE TABLE pin_comment_reads (
    pin_id UUID NOT NULL REFERENCES pins(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES party_users(id) ON DELETE CASCADE,
    last_read_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (pin_id, user_id)
);
