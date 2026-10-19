package com.doro.party.domain.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.concurrent.Executor;

@Slf4j
@Configuration
@EnableAsync
@EnableConfigurationProperties(PushProperties.class)
public class PushConfig {

    @Bean
    VapidSigner vapidSigner(PushProperties properties, Clock clock) {
        VapidSigner signer = VapidSigner.from(properties, clock);
        log.info("Web push {}", signer.enabled() ? "enabled" : "disabled (VAPID keys not configured)");
        return signer;
    }

    @Bean
    WebPushEncryptor webPushEncryptor() {
        return new WebPushEncryptor();
    }

    /** 리디렉션은 따라가지 않는다(허용 목록 밖으로 새지 않게). */
    @Bean
    WebPushClient webPushClient(VapidSigner signer, PushProperties properties) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(properties.requestTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new WebPushClient(http, signer, properties.requestTimeout());
    }

    /** 푸시 전용 스레드 풀. 대기열이 가득 차면 새 알림은 버리고(요청 스레드를 막지 않는다) 기록만 남긴다. */
    @Bean(name = "pushExecutor")
    Executor pushExecutor(PushProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("push-");
        executor.setCorePoolSize(properties.workerThreads());
        executor.setMaxPoolSize(properties.workerThreads());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setRejectedExecutionHandler((task, pool) -> log.warn("Push queue is full; dropping a notification"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
