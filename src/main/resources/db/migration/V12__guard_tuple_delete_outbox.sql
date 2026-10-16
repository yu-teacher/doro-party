-- DORO Party V12: Guard 튜플 삭제 아웃박스. 커밋은 됐는데 Guard 삭제가 실패하면(Guard 일시 장애) 지울 튜플을 여기에 남겨 두고 주기적으로 다시 지운다.
CREATE TABLE IF NOT EXISTS guard_tuple_delete_outbox (
    id UUID PRIMARY KEY,
    namespace VARCHAR(100) NOT NULL,
    object_id VARCHAR(200) NOT NULL,
    relation VARCHAR(100) NOT NULL,
    subject_namespace VARCHAR(100) NOT NULL,
    subject_id VARCHAR(200) NOT NULL,
    subject_relation VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT
);

CREATE INDEX IF NOT EXISTS idx_guard_delete_outbox_next_attempt ON guard_tuple_delete_outbox (next_attempt_at);
