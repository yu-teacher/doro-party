package com.doro.party.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** 사용량 상한과 서비스 시간대. 값은 환경변수로 바꾼다(application.yaml 의 party.*). */
@Configuration
@EnableConfigurationProperties(PartyLimitsConfig.PartyLimits.class)
public class PartyLimitsConfig {

    @ConfigurationProperties(prefix = "party.limits")
    public record PartyLimits(
            int maxMapsPerUser,
            int maxPinsPerMap,
            int maxTagsPerPin,
            int maxVisitsPerPin,
            int maxCommentsPerPin,
            int maxPhotosPerPin,
            long maxPhotoBytes,
            int maxFriends,
            int maxPendingFriendRequests,
            int inviteTtlDays,
            int maxSharesPerMap,
            int maxGroupsPerUser,
            int maxMembersPerGroup,
            int maxGroupsPerMap,
            int maxOverlayMaps,
            int maxOverlayPins
    ) {
    }

    /** "오늘"을 정하는 시계. 방문 날짜가 미래인지 판단할 때 서버 시간대가 아니라 서비스 시간대를 쓴다. */
    @Bean
    public Clock serviceClock(@org.springframework.beans.factory.annotation.Value("${party.timezone:Asia/Seoul}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
