package com.doro.party.infra.guard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PartySchemaInitializer implements ApplicationRunner {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 기본값은 application.yaml 의 doro.guard.http-url (환경변수 GUARD_HTTP_HOST/PORT) 한 곳에서만 정한다.
    @Value("${doro.guard.http-url}")
    private String guardHttpUrl;

    @Value("${doro.guard.http-timeout-ms}")
    private int guardHttpTimeoutMs;

    @Value("${doro.guard.service-token:}")
    private String guardServiceToken;

    /** Guard 가 응답하지 않아도 애플리케이션 기동이 무한정 멈추지 않도록 연결/읽기 타임아웃을 건다. */
    private RestTemplate newRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(guardHttpTimeoutMs);
        factory.setReadTimeout(guardHttpTimeoutMs);
        return new RestTemplate(factory);
    }

    /** Guard 가 아직 안 떠 있을 때 백그라운드로 다시 시도하는 간격과 최대 횟수. */
    @Value("${doro.guard.schema-sync-retry-seconds:15}")
    private long retryDelaySeconds;

    @Value("${doro.guard.schema-sync-max-retries:20}")
    private int maxRetries;

    @Override
    public void run(ApplicationArguments args) {
        if (syncOnce()) {
            return;
        }
        // 첫 시도가 실패했다: 서비스는 계속 기동하되, 성공할 때까지 백그라운드에서 재시도한다.
        // (재시도 없이 넘어가면 Guard 가 부팅 중이었던 경우 재시작 전까지 스키마가 조용히 비어 있게 된다.)
        Thread.ofVirtual().name("party-schema-sync-retry").start(this::retryUntilSynced);
    }

    void retryUntilSynced() {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Thread.sleep(Duration.ofSeconds(retryDelaySeconds));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            log.info("Retrying Guard schema sync ({}/{})", attempt, maxRetries);
            if (syncOnce()) {
                return;
            }
        }
        log.error("Guard schema sync did not succeed after {} retries - party types may be missing in Guard until restart", maxRetries);
    }

    /** @return 더 시도할 필요가 없으면 true(동기화 완료이거나 할 일이 없음), 나중에 다시 시도해야 하면 false */
    boolean syncOnce() {
        RestTemplate restTemplate = newRestTemplate();
        log.info("Checking DORO Guard Zanzibar schema synchronization...");
        try {
            String schemaUrl = guardHttpUrl + "/api/v1/guard/schema";
            ResponseEntity<String> response = restTemplate.exchange(
                    schemaUrl, HttpMethod.GET, new HttpEntity<>(guardHeaders()), String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Could not retrieve active schema from Guard: status={}", response.getStatusCode());
                return false;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String activeDsl = root.path("data").asText("");

            // party-schema.doro 읽기
            Resource partySchemaRes = resourceLoader.getResource("classpath:party-schema.doro");
            if (!partySchemaRes.exists()) {
                log.warn("party-schema.doro not found in classpath.");
                return true; // 재시도해도 달라지지 않는다
            }

            String partyDsl;
            try (InputStream is = partySchemaRes.getInputStream()) {
                partyDsl = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            // Guard 스키마 등록은 전체 교체다. 다른 서비스의 타입은 그대로 두고, 도로 파티 타입만 없으면 덧붙이고 달라졌으면 교체한다.
            PartySchemaMerger.Result merge = PartySchemaMerger.merge(activeDsl, partyDsl);
            if (!merge.replacedTypes().isEmpty()) {
                log.info("Updating changed party types in DORO Guard: {}", merge.replacedTypes());
            }
            if (!merge.changed()) {
                log.info("DORO Guard already contains every party schema type. Sync complete.");
                return true;
            }
            if (!merge.addedTypes().isEmpty()) {
                log.info("Registering missing party schema types in DORO Guard: {}", merge.addedTypes());
            }
            String combinedDsl = merge.mergedDsl();

            HttpHeaders headers = guardHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(Map.of("dsl", combinedDsl), headers);

            ResponseEntity<String> postRes = restTemplate.postForEntity(schemaUrl, request, String.class);
            if (postRes.getStatusCode().is2xxSuccessful()) {
                log.info("Successfully registered Party ReBAC schema to DORO Guard dynamically.");
                return true;
            }
            log.warn("Failed to register party schema: status={}", postRes.getStatusCode());
            return false;

        } catch (Exception e) {
            log.warn("Guard schema dynamic sync failed (Guard may be offline or unreachable): {}", e.getMessage());
            return false;
        }
    }

    private HttpHeaders guardHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (guardServiceToken != null && !guardServiceToken.isBlank()) {
            headers.set("X-Doro-Service-Token", guardServiceToken);
        }
        return headers;
    }
}
