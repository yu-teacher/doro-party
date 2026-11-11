package com.doro.party.config;

import com.doro.party.domain.user.sync.DeletedAccountAnonymizer;
import com.hunnit_beasts.doro.sdk.sync.DeletedUserHandler;
import com.hunnit_beasts.doro.sdk.sync.DeletedUsersProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Doro 연동 설정. 로그인·세션·토큰 갱신은 별도 서비스(doro-bff)가 맡고 게이트웨이가 {@code Authorization: Bearer} 를 붙여 주므로,
 * 이 서비스는 Bearer 토큰만 검증한다(SDK). 여기에는 로그인과 무관하게 필요한 탈퇴 동기화만 둔다.
 */
@Configuration
@EnableScheduling
public class DeletedSyncConfig {

    /** 탈퇴 동기화 설정({@code party.deleted-sync.*}: enabled, page-size, overlap, interval, initial-delay, 타임아웃). 로직은 SDK 가 맡는다. */
    @Bean
    @ConfigurationProperties(prefix = "party.deleted-sync")
    public DeletedUsersProperties deletedUsersProperties() {
        return new DeletedUsersProperties();
    }

    /** Doro 에서 영구 탈퇴한 사용자의 도로 파티 데이터를 익명화·삭제한다(멱등). */
    @Bean
    public DeletedUserHandler deletedUserHandler(DeletedAccountAnonymizer anonymizer) {
        return anonymizer::anonymize;
    }
}
