package com.doro.party.domain.share;

import com.doro.party.infra.guard.PartyGuard;

/** 친구에게 지도를 공유할 때의 권한: 보기만(VIEWER) / 핀 추가·수정까지(EDITOR). */
public enum ShareRole {
    VIEWER(PartyGuard.VIEWER),
    EDITOR(PartyGuard.EDITOR);

    private final String guardRelation;

    ShareRole(String guardRelation) {
        this.guardRelation = guardRelation;
    }

    /** Guard 스키마(party_map)에서 이 권한에 해당하는 관계 이름. */
    public String guardRelation() {
        return guardRelation;
    }
}
