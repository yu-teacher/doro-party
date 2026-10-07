-- DORO Party V4: 핀의 개인 기록 — 재방문 의사, 방문 기록, 사적 메모, 사진

ALTER TABLE pins ADD COLUMN revisit_intent VARCHAR(10) CHECK (revisit_intent IN ('AGAIN', 'ONCE'));

-- 방문 기록: 같은 핀을 여러 번 다녀온 날짜와 한 줄 후기. 지도를 볼 수 있는 사람에게 공유된다.
CREATE TABLE visit_logs (
    id UUID PRIMARY KEY,
    pin_id UUID NOT NULL REFERENCES pins(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES party_users(id) ON DELETE CASCADE,
    visited_on DATE NOT NULL,
    note VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_visit_logs_pin ON visit_logs(pin_id, visited_on DESC, created_at DESC);

-- 사적 메모: 핀과 사용자 한 쌍에 하나. 쓴 사람 본인에게만 보인다(핀 응답에는 절대 싣지 않는다).
CREATE TABLE pin_private_notes (
    pin_id UUID NOT NULL REFERENCES pins(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES party_users(id) ON DELETE CASCADE,
    body VARCHAR(2000) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (pin_id, user_id)
);
CREATE INDEX idx_pin_private_notes_user ON pin_private_notes(user_id);

-- 사진: 파일은 비공개 오브젝트 스토리지에 두고 DB 에는 키와 검증된 형식·크기만 저장한다.
CREATE TABLE pin_photos (
    id UUID PRIMARY KEY,
    pin_id UUID NOT NULL REFERENCES pins(id) ON DELETE CASCADE,
    uploaded_by UUID NOT NULL REFERENCES party_users(id) ON DELETE CASCADE,
    object_key VARCHAR(200) NOT NULL UNIQUE,
    content_type VARCHAR(30) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_pin_photos_pin ON pin_photos(pin_id, created_at);
