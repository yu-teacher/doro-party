package com.doro.party.domain.user.service;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.domain.user.dto.PartyUserDtos.UpdateProfileRequest;
import com.doro.party.domain.user.dto.PartyUserDtos.UserProfileResponse;
import com.doro.party.domain.user.entity.PartyUser;
import com.doro.party.domain.user.repository.PartyUserRepository;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartyUserService {

    private final PartyUserRepository userRepository;

    /** Doro 에서 처음 로그인한 사용자를 첫 요청 때 만든다(JIT). 이메일은 사용자명 후보를 만드는 데만 쓰고 저장하지 않는다. */
    @Transactional
    public PartyUser getOrCreateUser(DoroUser doroUser) {
        if (!doroUser.isAuthenticated()) {
            throw new PartyException(ErrorCode.UNAUTHORIZED);
        }
        return userRepository.findById(doroUser.userId()).orElseGet(() -> {
            // 이메일 등 계정 정보에서 만들지 않는다: 친구가 이 이름으로 나를 찾을 수 있으므로 무작위 값으로 시작하고 본인이 바꾼다.
            String username = UsernamePolicy.randomHandle();
            while (userRepository.existsByUsername(username)) {
                username = UsernamePolicy.randomHandle();
            }
            log.info("Provisioning new PartyUser for IAM userId={}", doroUser.userId());
            // 동시에 여러 요청이 들어와도 한 번만 만들고 나머지는 만들어진 행을 읽는다.
            userRepository.insertIfAbsent(doroUser.userId(), username, username, UserColors.forUser(doroUser.userId()));
            return userRepository.findById(doroUser.userId())
                    .orElseThrow(() -> new PartyException(ErrorCode.USER_NOT_FOUND));
        });
    }

    /** 닉네임과 사용자명을 바꾼다. 사용자명은 정리(소문자, @ 제거)한 뒤 형식·예약어·중복을 검사한다. */
    @Transactional
    public UserProfileResponse updateProfile(DoroUser doroUser, UpdateProfileRequest request) {
        PartyUser user = getOrCreateUser(doroUser);
        String nickname = request.nickname().strip();
        if (nickname.isEmpty() || nickname.codePoints().anyMatch(Character::isISOControl)) {
            throw new PartyException(ErrorCode.INVALID_INPUT, "닉네임에 사용할 수 없는 문자가 있습니다.");
        }
        String username = UsernamePolicy.normalize(request.username());
        if (!UsernamePolicy.isAllowed(username)) {
            throw new PartyException(ErrorCode.INVALID_INPUT,
                    "사용자명은 " + UsernamePolicy.MIN_LENGTH + "~" + UsernamePolicy.MAX_LENGTH + "자의 영문 소문자, 숫자, '_' 만 쓸 수 있고 예약된 이름은 쓸 수 없습니다.");
        }
        if (!username.equals(user.getUsername()) && userRepository.existsByUsername(username)) {
            throw new PartyException(ErrorCode.USERNAME_TAKEN);
        }
        user.updateProfile(nickname, username);
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 이름을 고른 다른 사용자가 먼저 저장했다(DB 유니크 제약이 최종 판정)
            throw new PartyException(ErrorCode.USERNAME_TAKEN);
        }
        return UserProfileResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfileById(UUID userId) {
        PartyUser user = userRepository.findById(userId)
                .orElseThrow(() -> new PartyException(ErrorCode.USER_NOT_FOUND));
        return UserProfileResponse.from(user);
    }
}
