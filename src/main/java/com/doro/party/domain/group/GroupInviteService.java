package com.doro.party.domain.group;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.common.util.Digests;
import com.doro.party.config.PartyLimitsConfig.PartyLimits;
import com.doro.party.domain.auth.SessionCrypto;
import com.doro.party.domain.friend.FriendDtos.InviteLinkResponse;
import com.doro.party.domain.group.GroupDtos.GroupInvitePreview;
import com.doro.party.domain.group.GroupDtos.JoinResult;
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
 * 모임 초대 링크(카톡 오픈채팅처럼 링크로 들어온다). 링크는 모임마다 하나이고 만료되며, 방장이 다시 만들면 이전 링크는 즉시 무효가 된다.
 * 멤버는 링크를 볼 수 있어 공유할 수 있지만 다시 만들거나 없애는 것은 방장만 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupInviteService {

    private static final int CODE_BYTES = 24;

    private final GroupInviteRepository invites;
    private final PartyGroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupService groupService;
    private final PartyUserRepository users;
    private final PartyUserService userService;
    private final SessionCrypto crypto;
    private final PartyLimits limits;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    @Transactional(readOnly = true)
    public Optional<InviteLinkResponse> current(UUID groupId) {
        return invites.findById(groupId)
                .filter(invite -> !invite.isExpired(Instant.now(clock)))
                .map(invite -> new InviteLinkResponse(crypto.decrypt(invite.getCodeEnc()), invite.getExpiresAt()));
    }

    @Transactional
    public InviteLinkResponse create(UUID groupId) {
        groups.findById(groupId).orElseThrow(() -> new PartyException(ErrorCode.GROUP_NOT_FOUND));
        byte[] bytes = new byte[CODE_BYTES];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now(clock).plus(Duration.ofDays(limits.inviteTtlDays()));
        invites.upsert(groupId, Digests.sha256Hex(code), crypto.encrypt(code), expiresAt);
        log.info("Group invite created: groupId={}", groupId);
        return new InviteLinkResponse(code, expiresAt);
    }

    @Transactional
    public void revoke(UUID groupId) {
        invites.findById(groupId).ifPresent(invites::delete);
    }

    /** 링크를 연 사람에게 어떤 모임인지 보여 준다. 없거나 만료된 코드는 모두 같은 404 다. */
    @Transactional(readOnly = true)
    public GroupInvitePreview preview(DoroUser doroUser, String code) {
        PartyGroup group = resolve(code);
        PartyUser owner = users.findById(group.getOwnerId()).orElseThrow(() -> new PartyException(ErrorCode.INVITE_NOT_FOUND));
        long count = members.countByGroupId(group.getId());
        return new GroupInvitePreview(group.getName(), (int) count, owner.getNickname(),
                members.existsById(GroupMember.Key.of(group.getId(), doroUser.userId())), count >= limits.maxMembersPerGroup());
    }

    @Transactional
    public JoinResult join(DoroUser doroUser, String code) {
        PartyUser me = userService.getOrCreateUser(doroUser);
        PartyGroup group = resolve(code);
        groupService.addMember(group.getId(), me.getId());
        return new JoinResult(group.getId(), group.getName());
    }

    private PartyGroup resolve(String code) {
        if (code == null || code.isBlank() || code.length() > 100) {
            throw new PartyException(ErrorCode.INVITE_NOT_FOUND);
        }
        UUID groupId = invites.findByCodeHash(Digests.sha256Hex(code))
                .filter(invite -> !invite.isExpired(Instant.now(clock)))
                .map(GroupInvite::getGroupId)
                .orElseThrow(() -> new PartyException(ErrorCode.INVITE_NOT_FOUND));
        return groups.findById(groupId).orElseThrow(() -> new PartyException(ErrorCode.INVITE_NOT_FOUND));
    }
}
