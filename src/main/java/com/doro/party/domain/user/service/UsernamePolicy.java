package com.doro.party.domain.user.service;

import java.util.Locale;
import java.util.Set;

/** 사용자명(@username) 생성·검증 규칙. 라우트와 충돌하는 이름과 이메일 원문 노출을 막는다. */
public final class UsernamePolicy {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 50;
    /** 중복 회피용 숫자 꼬리표(base + n)를 붙여도 최대 길이를 넘지 않도록 기본 이름은 이보다 짧게 자른다. */
    private static final int BASE_MAX_LENGTH = 40;

    /** 프런트 라우트·게이트웨이 경로와 겹치거나 운영자로 오인될 수 있는 이름. */
    private static final Set<String> RESERVED = Set.of(
            "admin", "administrator", "root", "system", "support", "staff", "moderator", "official",
            "api", "me", "write", "edit", "search", "maps", "map", "friends", "groups", "party", "settings", "login", "logout",
            "signup", "signin", "register", "auth", "iam", "oauth2", "portal", "logs", "loki", "media",
            "assets", "static", "public", "health", "actuator", "swagger", "docs", "feed",
            "notifications", "invite", "null", "undefined");

    private UsernamePolicy() {
    }

    public static boolean isReserved(String username) {
        return username != null && RESERVED.contains(username.toLowerCase(Locale.ROOT));
    }

    /**
     * 이메일 로컬파트에서 사용자명 후보를 만든다. 이메일이 없거나 쓸 수 없는 문자뿐이거나 너무 짧거나
     * 예약어이면 "user{번호}" 로 대체한다.
     */
    public static String baseFromEmail(String email, long userIndex) {
        String local = email == null ? "" : email.split("@", 2)[0];
        String base = local.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        if (base.length() > BASE_MAX_LENGTH) {
            base = base.substring(0, BASE_MAX_LENGTH);
        }
        if (base.length() < MIN_LENGTH || isReserved(base)) {
            return "user" + userIndex;
        }
        return base;
    }
}
