package com.doro.party.domain.user.service;

import com.doro.party.common.exception.ErrorCode;
import com.doro.party.common.exception.PartyException;
import com.doro.party.domain.user.dto.PartyUserDtos.UserProfileResponse;
import com.doro.party.domain.user.entity.PartyUser;
import com.doro.party.domain.user.repository.PartyUserRepository;
import com.hunnit_beasts.doro.sdk.domain.DoroUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            String base = UsernamePolicy.baseFromEmail(doroUser.email(), doroUser.userIndex());
            String username = base;
            int counter = 1;
            while (userRepository.existsByUsername(username)) {
                username = base + counter++;
            }
            log.info("Provisioning new PartyUser for IAM userId={}", doroUser.userId());
            // 동시에 여러 요청이 들어와도 한 번만 만들고 나머지는 만들어진 행을 읽는다.
            userRepository.insertIfAbsent(doroUser.userId(), username, username, UserColors.forUser(doroUser.userId()));
            return userRepository.findById(doroUser.userId())
                    .orElseThrow(() -> new PartyException(ErrorCode.USER_NOT_FOUND));
        });
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfileById(UUID userId) {
        PartyUser user = userRepository.findById(userId)
                .orElseThrow(() -> new PartyException(ErrorCode.USER_NOT_FOUND));
        return UserProfileResponse.from(user);
    }
}
