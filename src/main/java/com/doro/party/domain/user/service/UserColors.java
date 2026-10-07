package com.doro.party.domain.user.service;

import java.util.List;
import java.util.UUID;

/** 사용자별 핀 색상. 구분이 잘 되는 고정 팔레트에서 사용자 ID 로 정해 같은 사람은 항상 같은 색이다. */
final class UserColors {

    private static final List<String> PALETTE = List.of(
            "#E4572E", "#17BEBB", "#FFC914", "#76B041", "#8E5572", "#2E86AB",
            "#F28F3B", "#5B5F97", "#C8553D", "#2D936C", "#B5446E", "#3D5A80");

    private UserColors() {
    }

    static String forUser(UUID userId) {
        return PALETTE.get(Math.floorMod(userId.hashCode(), PALETTE.size()));
    }
}
