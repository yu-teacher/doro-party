package com.doro.party.domain.push;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 플랫폼 알림 서비스(doro-notify) 연결 설정(party.notify.*). 설계: Doro 저장소 docs/NOTIFICATION_SERVICE_DESIGN.md 12장.
 *
 * <p>mode: LEGACY(기본) = 지금처럼 파티가 직접 보내고 알림 서비스는 부르지 않는다.
 * SHADOW = 파티가 직접 보내면서 같은 알림을 알림 서비스에 {@code dryRun} 으로도 물어 결과를 비교만 한다(알림 서비스는 아무것도 보내지 않는다).
 * CENTRAL = 파티는 직접 보내지 않고 알림을 아웃박스에 쌓아 알림 서비스에 요청만 하며, 푸시 구독·설정 API 는 알림 서비스로 넘긴다(옛 화면이 그대로 동작한다).
 * 옛 발송 코드와 테이블은 그대로라서 모드를 LEGACY 로 돌리면 되돌아간다.
 *
 * @param baseUrl        알림 서비스 내부 주소(게이트웨이를 거치지 않는 Docker 내부 주소)
 * @param serviceToken   알림 서비스 내부 API 호출자 토큰(알림 서비스의 NOTIFY_INTERNAL_TOKENS 에 'party:<토큰>' 으로 등록한 값). 로그에 남기지 않는다
 * @param basePath       파티 앱 범위 경로. 알림 주소({@code ./?map=…})를 알림 서비스가 받는 절대 경로로 바꿀 때 쓴다
 * @param queueCapacity  비교 요청 대기열(가득 차면 비교만 건너뛴다. 실제 발송에는 영향이 없다)
 */
@ConfigurationProperties(prefix = "party.notify")
public record NotifyProperties(
        Mode mode,
        String baseUrl,
        String serviceToken,
        String basePath,
        Duration connectTimeout,
        Duration readTimeout,
        int queueCapacity
) {
    public enum Mode { LEGACY, SHADOW, CENTRAL }

    public NotifyProperties {
        mode = mode == null ? Mode.LEGACY : mode;
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://notify-api:8089" : baseUrl.strip().replaceAll("/+$", "");
        serviceToken = serviceToken == null ? "" : serviceToken.strip();
        basePath = basePath == null || basePath.isBlank() ? "/party/" : (basePath.endsWith("/") ? basePath : basePath + "/");
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(3) : readTimeout;
        queueCapacity = queueCapacity <= 0 ? 200 : queueCapacity;
    }

    /** 알림 서비스를 부를 준비가 됐는가(SHADOW 이고 토큰이 있다). 아니면 어떤 호출도 하지 않는다. */
    public boolean shadowing() {
        return mode == Mode.SHADOW && !serviceToken.isEmpty();
    }

    /** 알림 서비스가 발송과 푸시 설정을 맡는가(CENTRAL 이고 토큰이 있다). 토큰이 없으면 LEGACY 로 동작한다(알림이 사라지지 않게). */
    public boolean central() {
        return mode == Mode.CENTRAL && !serviceToken.isEmpty();
    }

    /** 파티 알림의 주소는 앱 범위 기준 상대 주소({@code ./?map=…})다. 알림 서비스는 앱 범위로 시작하는 절대 경로만 받는다. */
    public String toNotifyUrl(String url) {
        if (url.startsWith("./")) {
            return basePath + url.substring(2);
        }
        return url;
    }
}
