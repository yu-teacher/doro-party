package com.doro.party.domain.user.service;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 사용자명(@username) 생성·검증 규칙. 이메일 등 계정 정보에서 만들지 않고, 라우트와 충돌하는 이름을 막는다. */
public final class UsernamePolicy {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 30;

    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9_]{" + MIN_LENGTH + "," + MAX_LENGTH + "}$");
    private static final String HANDLE_PREFIX = "user";
    private static final int HANDLE_SUFFIX_LENGTH = 8;
    private static final String HANDLE_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 프런트 라우트·게이트웨이 경로와 겹치거나 운영자로 오인될 수 있는 이름. */
    private static final Set<String> RESERVED = Set.of(
            "admin", "administrator", "root", "system", "support", "staff", "moderator", "official",
            "api", "me", "write", "edit", "search", "maps", "map", "friends", "groups", "party", "settings", "login", "logout",
            "signup", "signin", "register", "auth", "iam", "oauth2", "portal", "logs", "loki", "media",
            "assets", "static", "public", "health", "actuator", "swagger", "docs", "feed",
            "notifications", "invite", "null", "undefined");

    private UsernamePolicy() {
    }

    /** 쓸 수 있는 형식인지(소문자·숫자·밑줄, 3~30자)와 예약어가 아닌지. */
    public static boolean isAllowed(String username) {
        return username != null && FORMAT.matcher(username).matches() && !isReserved(username);
    }

    public static boolean isReserved(String username) {
        return username != null && RESERVED.contains(username.toLowerCase(Locale.ROOT));
    }

    /** 입력을 사용자명 형식으로 정리한다(앞뒤 공백 제거, 앞의 @ 제거, 소문자). */
    public static String normalize(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.startsWith("@")) {
            value = value.substring(1);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    /** 처음 가입한 사용자에게 줄 임시 사용자명. 이메일 같은 계정 정보와 무관한 무작위 값이라 아무것도 드러내지 않는다. */
    public static String randomHandle() {
        StringBuilder handle = new StringBuilder(HANDLE_PREFIX);
        for (int i = 0; i < HANDLE_SUFFIX_LENGTH; i++) {
            handle.append(HANDLE_ALPHABET.charAt(RANDOM.nextInt(HANDLE_ALPHABET.length())));
        }
        return handle.toString();
    }
}
