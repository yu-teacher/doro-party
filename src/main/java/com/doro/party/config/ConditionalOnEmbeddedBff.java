package com.doro.party.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** BFF 를 이 서비스 안에서 돌릴 때({@code party.auth.mode=embedded}, 기본값)만 빈을 만든다. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ConditionalOnProperty(prefix = "party.auth", name = "mode", havingValue = "embedded", matchIfMissing = true)
public @interface ConditionalOnEmbeddedBff {
}
