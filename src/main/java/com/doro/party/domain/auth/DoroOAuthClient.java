package com.doro.party.domain.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Map;

/** Doro IAM 의 OAuth 엔드포인트(토큰 교환·갱신·폐기)를 서버 간 호출로 사용하는 클라이언트. */
@Slf4j
@Component
public class DoroOAuthClient {

    /** 발급된 토큰 묶음. expiresAt 은 액세스 토큰 만료 시각. */
    public record TokenSet(String accessToken, String refreshToken, Instant accessExpiresAt) {}

    /** IAM 이 요청을 명시적으로 거부했다(잘못되었거나 폐기·만료된 코드/토큰). 다시 시도해도 같은 결과다. */
    public static class RejectedException extends RuntimeException {
        public RejectedException(String message) {
            super(message);
        }
    }

    /** IAM 에 닿지 못했거나 5xx 였다. 일시적인 장애일 수 있어 세션은 유지한다. */
    public static class UnavailableException extends RuntimeException {
        public UnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final BffProperties props;
    private final RestClient http;

    public DoroOAuthClient(BffProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) props.getHttpConnectTimeout().toMillis());
        factory.setReadTimeout((int) props.getHttpReadTimeout().toMillis());
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    /** 브라우저를 보낼 인가 요청 주소 (인가 코드 + PKCE S256). */
    public String buildAuthorizeUrl(String state, String codeChallenge) {
        return UriComponentsBuilder.fromUriString(props.getAuthorizeUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", props.getClientId())
                .queryParam("redirect_uri", props.getRedirectUri())
                .queryParam("scope", props.getScope())
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUriString();
    }

    public TokenSet exchangeCode(String code, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", props.getRedirectUri());
        form.add("client_id", props.getClientId());
        form.add("code_verifier", codeVerifier);
        return toTokenSet(post(props.getTokenUrl(), form));
    }

    /** 리프레시 토큰은 회전된다: 응답의 새 리프레시 토큰으로 반드시 교체해야 한다. */
    public TokenSet refresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("client_id", props.getClientId());
        return toTokenSet(post(props.getTokenUrl(), form));
    }

    /** RFC 7009. 로그아웃 때 IAM 세션까지 끝낸다. 실패해도 호출자가 로컬 로그아웃을 계속할 수 있게 예외를 던진다. */
    public void revoke(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", refreshToken);
        form.add("client_id", props.getClientId());
        post(props.getRevokeUrl(), form);
    }

    private Map<String, Object> post(String url, MultiValueMap<String, String> form) {
        try {
            Map<String, Object> body = http.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            return body != null ? body : Map.of();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new RejectedException("IAM 이 요청을 거부했습니다 (HTTP " + e.getStatusCode().value() + ")");
            }
            throw new UnavailableException("IAM 오류 응답 (HTTP " + e.getStatusCode().value() + ")", e);
        } catch (RestClientException e) {
            throw new UnavailableException("IAM 에 연결할 수 없습니다", e);
        }
    }

    private TokenSet toTokenSet(Map<String, Object> body) {
        Object access = body.get("access_token");
        Object refresh = body.get("refresh_token");
        Object expiresIn = body.get("expires_in");
        if (!(access instanceof String accessToken) || accessToken.isBlank()
                || !(refresh instanceof String refreshToken) || refreshToken.isBlank()
                || !(expiresIn instanceof Number seconds)) {
            throw new RejectedException("토큰 응답 형식이 올바르지 않습니다");
        }
        return new TokenSet(accessToken, refreshToken, Instant.now().plusSeconds(seconds.longValue()));
    }
}
