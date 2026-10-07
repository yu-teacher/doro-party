package com.doro.party.config;

import com.doro.party.domain.auth.BffProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(BffProperties.class)
@EnableScheduling
public class BffConfig {
}
