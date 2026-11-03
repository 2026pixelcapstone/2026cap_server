package com.expansion.server.domain.user.service;

import com.expansion.server.domain.user.entity.User;
import com.expansion.server.domain.user.repository.UserRepository;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Objects;

/**
 * 사용자 행을 잠그고 활동 중인지 확인 — 회원 탈퇴(AccountWithdrawalService)와 경합하는 작업
 * (커미션 생성·지원 수락·팔로우)이 탈퇴한 사람 명의로 새 기록을 만들지 않게 한 줄로 세운다.
 * 교착을 피하려고 ①트랜잭션 맨 앞에서 ②id 오름차순으로 잠근다(탈퇴도 자기 행을 맨 먼저 잠금).
 */
@Component
@RequiredArgsConstructor
public class ActiveUserLock {

    private final UserRepository userRepository;

    public void lockAll(Long... userIds) {
        Arrays.stream(userIds).filter(Objects::nonNull).distinct().sorted().forEach(id -> {
            User user = userRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
            if (!user.isActive()) {
                throw new CustomException(ErrorCode.USER_NOT_FOUND);
            }
        });
    }
}
