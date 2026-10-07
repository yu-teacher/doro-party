package com.doro.party.domain.share;

import com.doro.party.infra.guard.PartyGuard;

/**
 * 지도를 친구 전체에게 공개하는 범위. NONE 은 공개하지 않음, VIEWER 는 보기만, EDITOR 는 핀 추가·수정까지.
 * 사람을 한 명씩 고르는 공유(ShareRole)와 별개로, 지금 친구와 앞으로 생길 친구 모두에게 한꺼번에 적용된다.
 */
public enum FriendAccess {
    NONE(null),
    VIEWER(PartyGuard.VIEWER),
    EDITOR(PartyGuard.EDITOR);

    private final String guardRelation;

    FriendAccess(String guardRelation) {
        this.guardRelation = guardRelation;
    }

    public boolean isPublic() {
        return this != NONE;
    }

    /** 지도(party_map)에서 이 범위에 해당하는 관계 이름. NONE 에는 없다. */
    public String guardRelation() {
        if (guardRelation == null) {
            throw new IllegalStateException("공개하지 않는 범위에는 Guard 관계가 없다");
        }
        return guardRelation;
    }
}
