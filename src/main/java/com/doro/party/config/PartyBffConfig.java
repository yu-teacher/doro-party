package com.doro.party.config;

import com.doro.party.domain.user.service.PartyUserService;
import com.hunnit_beasts.doro.sdk.bff.BffCrypto;
import com.hunnit_beasts.doro.sdk.bff.BffProperties;
import com.hunnit_beasts.doro.sdk.bff.BffUserHook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * BFF 로그인 설정. {@code party.auth.mode} 가 {@code gateway} 면 로그인·세션·토큰 갱신을 별도 서비스(doro-bff)가 맡고
 * 게이트웨이가 {@code Authorization: Bearer} 를 붙여 주므로 이 서비스에는 아래 BFF 빈이 만들어지지 않는다. {@code embedded}(기본)면 로그인 로직(세션·로그인 CSRF·토큰 갱신·로그아웃)은 SDK(doro-sdk 의 bff 패키지)가 맡고, 도로 파티는 설정 값과
 * 저장소({@link com.doro.party.domain.auth.PartyBffStores}), 로그인 직후의 사용자 생성만 제공한다.
 * 설정 키는 그대로 {@code party.auth.*}, 환경변수 이름도 그대로다.
 */
@Configuration
@EnableScheduling
public class PartyBffConfig {

    /** 도로 파티의 기본값을 깔고 {@code party.auth.*} 설정이 그 위에 덮어쓴다. */
    @Bean
    @ConditionalOnEmbeddedBff
    @ConfigurationProperties(prefix = "party.auth")
    public BffProperties bffProperties() {
        return defaults();
    }

    /** 이 서비스의 기본값(SDK 의 일반 기본값과 다른 부분). 시험도 같은 기본값을 쓴다. */
    public static BffProperties defaults() {
        BffProperties props = new BffProperties();
        props.setClientId("doro-party");
        props.setCookieName("doro_party_session");
        props.setLoginCookieName("doro_party_login");
        props.setCsrfHeader("X-Party-Csrf");
        // 도로 파티 웹은 라우터 기준 경로(/maps/42)를 보내고, 서버가 웹 기준 경로(/party)를 붙여 이동시킨다.
        props.setReturnPathRelativeToWebBase(true);
        return props;
    }

    /**
     * 초대 코드 암호화(저장한 초대 링크를 다시 보여 주려고 AES-GCM 으로 암호화해 둔다). 로그인 방식(embedded/gateway)과 무관하게 필요하다.
     * 세션 암호화 키({@code party.auth.session-encryption-key}, 환경변수 PARTY_SESSION_KEY)를 그대로 쓰므로 gateway 모드에서도
     * 이 값을 지우거나 바꾸면 저장된 초대 코드를 복호화하지 못한다. (embedded 에서는 SDK 의 BFF 가 같은 키로 같은 빈을 쓴다)
     */
    @Bean
    @ConditionalOnMissingBean
    public BffCrypto inviteCrypto(@Value("${party.auth.session-encryption-key:}") String key) {
        return new BffCrypto(key);
    }

    /** 로그인에 성공하면 도로 파티 사용자 행을 만들거나 갱신한다. */
    @Bean
    @ConditionalOnEmbeddedBff
    public BffUserHook bffUserHook(PartyUserService userService) {
        return user -> userService.getOrCreateUser(user);
    }
}
