package com.expansion.server.global.security;

import com.expansion.server.domain.user.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 토큰 주인의 계정 상태 확인 — 탈퇴·정지 후에도 이미 발급된 access 토큰(최대 1시간)으로 계속 쓰는 것을 막는다.
 * 요청마다 DB를 보면 부담이라 결과를 30초 캐시(탈퇴 시엔 evict로 즉시 반영, 정지 등 다른 경로는 최대 30초 지연).
 * 캐시는 서버 메모리라 '즉시 반영'은 지금 구조(서버 한 대) 기준 — 서버를 여러 대로 늘리면 다른 서버는 최대 30초 늦게 반영.
 * auth.status-check.enabled=false면 확인을 끈다(문제 생기면 설정 한 줄로 이전 동작 복귀).
 */
@Slf4j
@Component
public class UserStatusChecker {

    private final UserRepository userRepository;
    private final boolean enabled;
    private final Cache<Long, Boolean> activeCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .maximumSize(10_000)
            .build();

    public UserStatusChecker(UserRepository userRepository,
                             @Value("${auth.status-check.enabled:true}") boolean enabled) {
        this.userRepository = userRepository;
        this.enabled = enabled;
    }

    /** 활동 중인 계정인지(없는 계정·탈퇴·정지는 false) */
    public boolean isActive(Long userId) {
        if (!enabled || userId == null) return true;
        return activeCache.get(userId, id -> userRepository.findStatusById(id)
                .map("ACTIVE"::equals)
                .orElse(false));
    }

    /** 상태가 바뀐 직후(탈퇴 등) 캐시를 비워 바로 반영 */
    public void evict(Long userId) {
        activeCache.invalidate(userId);
    }
}
