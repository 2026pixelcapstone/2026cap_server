package com.expansion.server.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 실행 설정 — 현재는 '비밀번호 찾기' 메일 발송 전용.
 * 메일 발송(SMTP 수백 ms~수 초)을 요청 스레드 밖으로 빼서, 가입된 이메일이든 아니든
 * 응답 시간이 같게 만든다(응답 시간으로 가입 여부를 알아내는 것 방지).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String MAIL_EXECUTOR = "mailExecutor";

    @Bean(name = MAIL_EXECUTOR)
    public Executor mailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);        // 넘치면 TaskRejectedException → 발송 생략(로그)
        executor.setThreadNamePrefix("mail-");
        executor.initialize();
        return executor;
    }
}
