package com.doro.party.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 사용량 상한. 값은 환경변수로 바꾼다(application.yaml 의 party.limits). */
@Configuration
@EnableConfigurationProperties(PartyLimitsConfig.PartyLimits.class)
public class PartyLimitsConfig {

    @ConfigurationProperties(prefix = "party.limits")
    public record PartyLimits(int maxMapsPerUser, int maxPinsPerMap, int maxTagsPerPin) {
    }
}
