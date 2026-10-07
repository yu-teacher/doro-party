package com.doro.party.infra.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 사진 저장소 연결 정보(party.storage.*). 비밀은 환경변수로만 받는다. */
@ConfigurationProperties(prefix = "party.storage")
public record StorageProperties(String endpoint, String accessKey, String secretKey, String bucket) {
}
