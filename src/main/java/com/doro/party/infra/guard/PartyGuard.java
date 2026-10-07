package com.doro.party.infra.guard;

/** Guard 스키마(party-schema.doro)의 타입·관계 이름. {@code @DoroGuard} 에 쓰려면 컴파일 상수여야 한다. */
public final class PartyGuard {

    public static final String MAP = "party_map";
    public static final String GROUP = "party_group";
    public static final String USER = "user";

    public static final String OWNER = "owner";
    public static final String EDITOR = "editor";
    public static final String VIEWER = "viewer";
    public static final String MEMBER = "member";

    private PartyGuard() {
    }
}
