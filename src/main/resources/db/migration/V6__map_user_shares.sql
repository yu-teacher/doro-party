-- DORO Party V6: 지도를 특정 친구에게 공유(viewer/editor)
-- 실제 접근 판정은 Guard 튜플이 하고, 이 테이블은 "누구에게 공유했는지" 목록과 회수(친구 해제·지도 삭제)를 위한 원본이다.

CREATE TABLE map_user_shares (
    map_id UUID NOT NULL REFERENCES party_maps(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES party_users(id) ON DELETE CASCADE,
    role VARCHAR(10) NOT NULL CHECK (role IN ('VIEWER', 'EDITOR')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (map_id, user_id)
);
CREATE INDEX idx_map_user_shares_user ON map_user_shares(user_id);
