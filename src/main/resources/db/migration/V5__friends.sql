-- DORO Party V5: 친구와 초대 링크

-- 친구 관계 한 쌍에 행 하나. 방향과 상관없이 같은 쌍이 두 번 생기지 않도록 두 사용자 ID 를 작은 쪽/큰 쪽으로 정렬해 저장한다.
CREATE TABLE friendships (
    id UUID PRIMARY KEY,
    user_low_id UUID NOT NULL REFERENCES party_users(id) ON DELETE CASCADE,
    user_high_id UUID NOT NULL REFERENCES party_users(id) ON DELETE CASCADE,
    requester_id UUID NOT NULL REFERENCES party_users(id) ON DELETE CASCADE,
    status VARCHAR(10) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_friendship_pair UNIQUE (user_low_id, user_high_id),
    CONSTRAINT ck_friendship_order CHECK (user_low_id < user_high_id),
    CONSTRAINT ck_friendship_requester CHECK (requester_id = user_low_id OR requester_id = user_high_id)
);
CREATE INDEX idx_friendships_high ON friendships(user_high_id);

-- 친구 초대 링크: 사용자마다 하나. 코드는 해시로 찾고, 링크를 다시 보여 줄 수 있게 암호화한 원문도 둔다.
CREATE TABLE friend_invites (
    owner_id UUID PRIMARY KEY REFERENCES party_users(id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL UNIQUE,
    code_enc TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
