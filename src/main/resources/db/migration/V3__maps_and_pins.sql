-- DORO Party V3: 지도와 핀
-- 지도는 사람당 여러 개, 핀은 지도에 속한다. 접근 권한(owner/editor/viewer)은 Guard 튜플로 관리하고 DB 에는 두지 않는다.

CREATE TABLE party_maps (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES party_users(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_party_maps_owner ON party_maps(owner_id, created_at DESC);

CREATE TABLE pins (
    id UUID PRIMARY KEY,
    map_id UUID NOT NULL REFERENCES party_maps(id) ON DELETE CASCADE,
    created_by UUID NOT NULL REFERENCES party_users(id) ON DELETE CASCADE,
    lat DOUBLE PRECISION NOT NULL CHECK (lat BETWEEN -90 AND 90),
    lng DOUBLE PRECISION NOT NULL CHECK (lng BETWEEN -180 AND 180),
    name VARCHAR(100) NOT NULL,
    shared_memo VARCHAR(2000),
    status VARCHAR(10) NOT NULL CHECK (status IN ('WISH', 'VISITED')),
    rating INT CHECK (rating BETWEEN 1 AND 5),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_pins_map ON pins(map_id, created_at);
CREATE INDEX idx_pins_created_by ON pins(created_by);

CREATE TABLE pin_tags (
    pin_id UUID NOT NULL REFERENCES pins(id) ON DELETE CASCADE,
    tag VARCHAR(30) NOT NULL,
    PRIMARY KEY (pin_id, tag)
);
CREATE INDEX idx_pin_tags_tag ON pin_tags(tag);
