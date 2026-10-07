package com.doro.party.domain.friend;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.common.util.Digests;
import com.doro.party.config.PartyLimitsConfig.PartyLimits;
import com.doro.party.domain.auth.SessionCrypto;
import com.doro.party.domain.friend.FriendDtos.AcceptResult;
import com.doro.party.domain.friend.FriendDtos.InviteLinkResponse;
import com.doro.party.domain.friend.FriendDtos.InvitePreview;
import com.doro.party.domain.user.dto.PartyUserDtos.UserSummary;
import com.doro.party.domain.user.entity.PartyUser;
import com.doro.party.domain.user.repository.PartyUserRepository;
import com.doro.party.domain.user.service.PartyUserService;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * 친구 초대 링크. 링크를 가진 사람이 열어서 수락하면 바로 친구가 된다(링크를 만든 사람의 의도와 수락한 사람의 행동이 곧 상호 동의).
 * 링크는 사용자마다 하나이고 만료되며, 다시 만들면 이전 링크는 즉시 무효가 된다. 코드는 해시로만 찾는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FriendInviteService {

    private static final int CODE_BYTES = 24;

    private final FriendInviteRepository invites;
    private final FriendService friendService;
    private final PartyUserRepository users;
    private final PartyUserService userService;
    private final SessionCrypto crypto;
    private final PartyLimits limits;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    /** 내 현재 링크. 없거나 만료됐으면 비어 있다. */
    @Transactional(readOnly = true)
    public Optional<InviteLinkResponse> current(DoroUser doroUser) {
        return invites.findById(doroUser.userId())
                .filter(invite -> !invite.isExpired(Instant.now(clock)))
                .map(invite -> new InviteLinkResponse(crypto.decrypt(invite.getCodeEnc()), invite.getExpiresAt()));
    }

    /** 새 링크를 만든다. 이전 링크는 바로 쓸 수 없게 된다. */
    @Transactional
    public InviteLinkResponse create(DoroUser doroUser) {
        PartyUser me = userService.getOrCreateUser(doroUser);
        byte[] bytes = new byte[CODE_BYTES];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now(clock).plus(Duration.ofDays(limits.inviteTtlDays()));
        invites.upsert(me.getId(), Digests.sha256Hex(code), crypto.encrypt(code), expiresAt);
        log.info("Friend invite created: ownerId={}", me.getId());
        return new InviteLinkResponse(code, expiresAt);
    }

    @Transactional
    public void revoke(DoroUser doroUser) {
        invites.findById(doroUser.userId()).ifPresent(invites::delete);
    }

    /** 링크를 연 사람에게 누구의 초대인지 보여 준다. 없거나 만료된 코드는 모두 같은 404 다. */
    @Transactional(readOnly = true)
    public InvitePreview preview(DoroUser doroUser, String code) {
        PartyUser inviter = resolve(code);
        boolean self = inviter.getId().equals(doroUser.userId());
        return new InvitePreview(UserSummary.from(inviter), self, !self && friendService.areFriends(doroUser.userId(), inviter.getId()));
    }

    @Transactional
    public AcceptResult accept(DoroUser doroUser, String code) {
        PartyUser me = userService.getOrCreateUser(doroUser);
        PartyUser inviter = resolve(code);
        friendService.connect(me.getId(), inviter.getId());
        return new AcceptResult(UserSummary.from(inviter));
    }

    private PartyUser resolve(String code) {
        if (code == null || code.isBlank() || code.length() > 100) {
            throw new PartyException(ErrorCode.INVITE_NOT_FOUND);
        }
        UUID ownerId = invites.findByCodeHash(Digests.sha256Hex(code))
                .filter(invite -> !invite.isExpired(Instant.now(clock)))
                .map(FriendInvite::getOwnerId)
                .orElseThrow(() -> new PartyException(ErrorCode.INVITE_NOT_FOUND));
        return users.findById(ownerId).orElseThrow(() -> new PartyException(ErrorCode.INVITE_NOT_FOUND));
    }
}
