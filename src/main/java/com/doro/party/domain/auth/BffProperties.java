package com.doro.party.domain.auth;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/** 블로그 BFF 로그인 설정(party.auth.*). URL 과 비밀은 환경변수로만 바꾼다. */
@Getter
@Setter
@ConfigurationProperties(prefix = "party.auth")
public class BffProperties {

    /** Doro 에 등록한 이 블로그의 OAuth 클라이언트 ID */
    private String clientId = "doro-party";
    /** 브라우저가 이동하는 인가 엔드포인트(공개 주소) */
    private String authorizeUrl = "http://localhost:28080/oauth2/authorize";
    /** 서버가 직접 호출하는 토큰 엔드포인트(내부 주소 가능) */
    private String tokenUrl = "http://localhost:28080/oauth2/token";
    /** 서버가 직접 호출하는 토큰 폐기 엔드포인트(로그아웃 시 IAM 세션 종료) */
    private String revokeUrl = "http://localhost:28080/oauth2/revoke";
    /** Doro 에 등록한 redirect_uri. 이 블로그의 콜백 주소(공개 주소)와 정확히 같아야 한다. */
    private String redirectUri = "http://localhost:5173/api/v1/bff/callback";
    private String scope = "openid profile email";
    private String cookieName = "doro_party_session";
    /** HTTPS 에서만 쿠키를 보낸다. 로컬 HTTP 개발에서만 false. */
    private boolean cookieSecure = true;
    /** 웹이 마운트된 하위 경로(예: /party). 세션 쿠키 경로와 로그인 후 이동 경로의 접두사로 쓴다. 루트에 마운트하면 비워 둔다. */
    private String webBasePath = "";
    /** 로그인 후 세션의 절대 수명 */
    private Duration sessionTtl = Duration.ofDays(30);
    /** 인가 요청을 보낸 뒤 콜백이 돌아와야 하는 시간 */
    private Duration loginAttemptTtl = Duration.ofMinutes(10);
    /** 액세스 토큰 만료 이 시간 전부터 미리 갱신한다 */
    private Duration refreshMargin = Duration.ofSeconds(60);
    /** 쿠키 인증으로 상태를 바꾸는 요청에 반드시 붙어야 하는 헤더(CSRF 방어: 다른 사이트는 커스텀 헤더를 붙일 수 없다) */
    private String csrfHeader = "X-Party-Csrf";
    private Duration httpConnectTimeout = Duration.ofSeconds(3);
    private Duration httpReadTimeout = Duration.ofSeconds(5);

    /** 쿠키 Path 속성. 하위 경로 마운트면 그 경로로 제한해 같은 도메인의 다른 서비스에 쿠키가 가지 않게 한다. */
    public String cookiePath() {
        return webBasePath == null || webBasePath.isBlank() ? "/" : webBasePath;
    }

    /** 앱 내부 경로(/maps 등)를 브라우저가 이동할 실제 경로로 바꾼다. */
    public String webPath(String appPath) {
        return (webBasePath == null ? "" : webBasePath) + appPath;
    }

    /** redirect_uri 의 origin(scheme://host[:port]). Origin 헤더 검증에 쓴다. */
    public String publicOrigin() {
        URI uri = URI.create(redirectUri);
        return uri.getScheme() + "://" + uri.getAuthority();
    }
}
