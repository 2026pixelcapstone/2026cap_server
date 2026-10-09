package com.expansion.server.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * @Scheduled 활성화 — 현재 사용처: 주간 챌린지 교체(ChallengeRollover).
 * 전용 스케줄러를 'taskScheduler' 이름으로 둔다. 없으면 웹소켓(STOMP)이 만든 하트비트용
 * messageBrokerTaskScheduler를 빌려 써서 채팅 하트비트와 같은 스레드에서 돈다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("scheduler-");
        return scheduler;
    }
}
