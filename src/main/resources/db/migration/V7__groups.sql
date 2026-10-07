-- DORO Party V7: 모임(카톡방처럼 사람들을 묶는 단위)과 모임에 공유한 지도
-- 접근 판정은 Guard 가 한다: party_group:G#member@user:U 와 party_map:M#viewer@party_group:G#member.
-- 이 테이블들은 목록·회수·정리를 위한 원본이다.

CREATE TABLE party_groups (
    id UUID PRIMARY KEY,
    name VARCHAR(30) NOT NULL,
    owner_id UUID NOT NULL REFERENCES party_users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE party_group_members (
    group_id UUID NOT NULL REFERENCES party_groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES party_users(id) ON DELETE CASCADE,
    role VARCHAR(10) NOT NULL CHECK (role IN ('OWNER', 'MEMBER')),
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (group_id, user_id)
);
CREATE INDEX idx_group_members_user ON party_group_members(user_id);
-- 모임마다 방장은 한 명
CREATE UNIQUE INDEX uq_group_single_owner ON party_group_members(group_id) WHERE role = 'OWNER';

-- 모임 초대 링크: 모임마다 하나(친구 초대 링크와 같은 방식)
CREATE TABLE group_invites (
    group_id UUID PRIMARY KEY REFERENCES party_groups(id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL UNIQUE,
    code_enc TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- 지도를 모임에 공유(모임 멤버는 모두 viewer). 지도 주인은 그대로이고, 누가 공유했는지를 남긴다.
CREATE TABLE map_group_shares (
    map_id UUID NOT NULL REFERENCES party_maps(id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES party_groups(id) ON DELETE CASCADE,
    shared_by UUID NOT NULL REFERENCES party_users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (map_id, group_id)
);
CREATE INDEX idx_map_group_shares_group ON map_group_shares(group_id);
