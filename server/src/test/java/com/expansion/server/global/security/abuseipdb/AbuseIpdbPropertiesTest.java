package com.expansion.server.global.security.abuseipdb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * AbuseIpdbProperties compact 생성자 검증 회귀 테스트.
 *
 * 배경(2026-06-29 프로덕션 장애): 기존 prod yml에 Report 설정 줄이 없으면 필드가 0으로
 * 바인딩되는데, 무조건 하한 검증을 하던 초기 구현이 기동을 막았다.
 * → 검증은 report-enabled=true(신고 활성)일 때만 수행해야 한다.
 */
class AbuseIpdbPropertiesTest {

    /** 신고 비활성이면 설정 줄 누락(=0 바인딩)이어도 기동을 막지 않아야 한다. */
    @Test
    void reportDisabled_allowsZeroThresholds() {
        assertDoesNotThrow(() -> new AbuseIpdbProperties(
                true, "key", 75, 3600, 2000,
                false, 0, 0, 0));
    }

    /** 신고 활성인데 임계값이 0이면 첫 실패부터 신고되므로 기동 시 fail-fast 해야 한다. */
    @Test
    void reportEnabled_rejectsZeroLoginFailThreshold() {
        assertThrows(IllegalArgumentException.class, () -> new AbuseIpdbProperties(
                true, "key", 75, 3600, 2000,
                true, 0, 10, 60));
    }

    @Test
    void reportEnabled_rejectsZeroFailWindow() {
        assertThrows(IllegalArgumentException.class, () -> new AbuseIpdbProperties(
                true, "key", 75, 3600, 2000,
                true, 5, 0, 60));
    }

    @Test
    void reportEnabled_rejectsZeroCooldown() {
        assertThrows(IllegalArgumentException.class, () -> new AbuseIpdbProperties(
                true, "key", 75, 3600, 2000,
                true, 5, 10, 0));
    }

    /** 신고 활성 + 정상 값이면 통과. */
    @Test
    void reportEnabled_acceptsValidValues() {
        assertDoesNotThrow(() -> new AbuseIpdbProperties(
                true, "key", 75, 3600, 2000,
                true, 5, 10, 60));
    }
}
