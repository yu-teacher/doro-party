package com.doro.party.domain.group;

import com.doro.party.infra.guard.PartyGuard;

/** 모임 안의 역할: 방장(OWNER) / 멤버(MEMBER). */
public enum GroupRole {
    OWNER(PartyGuard.OWNER),
    MEMBER(PartyGuard.MEMBER);

    private final String guardRelation;

    GroupRole(String guardRelation) {
        this.guardRelation = guardRelation;
    }

    /** Guard 스키마(party_group)에서 이 역할에 해당하는 관계 이름. */
    public String guardRelation() {
        return guardRelation;
    }
}
