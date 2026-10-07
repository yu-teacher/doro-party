-- 지도를 친구 전체에게 공개하는 범위. NONE 이면 공개하지 않는다.
-- 원본은 이 열이고, Guard 에는 party_map:<id>#<viewer|editor>@party_friends:<주인>#friend 튜플이 이에 맞춰 쓰인다.
ALTER TABLE party_maps
    ADD COLUMN friend_access VARCHAR(10) NOT NULL DEFAULT 'NONE',
    ADD CONSTRAINT chk_party_maps_friend_access CHECK (friend_access IN ('NONE', 'VIEWER', 'EDITOR'));

-- 친구 지도 둘러보기: 공개된 지도만 주인별로 찾는다
CREATE INDEX idx_party_maps_friend_access ON party_maps (owner_id) WHERE friend_access <> 'NONE';
