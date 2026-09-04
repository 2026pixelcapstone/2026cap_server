package com.expansion.server.global.config;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ⚠️ 1회용 마이그레이션 전략 — V18(chat 테이블 중복) 수정 대응.
 *
 * V18에서 chat_rooms/chat_messages의 중복 CREATE를 제거하면서 파일 내용이 바뀌었고,
 * 이미 V18을 적용한 기존 DB(프로덕션·팀원 로컬)는 Flyway 체크섬 불일치로 기동이 막힌다.
 * 기본 migrate 전에 repair()를 한 번 돌려 flyway_schema_history의 체크섬을 새 파일 값으로
 * 갱신한다. 기존 DB엔 이미 제약·인덱스가 있어 재실행 없이 통과하고, 새 DB(from-scratch)는
 * 수정된 V18(ALTER + CREATE INDEX)이 정상 실행된다.
 *
 * 🔴 전 환경이 한 번씩 기동해 체크섬이 맞춰지면 이 빈을 제거한다(상시 repair는 지양).
 * 관련: TROUBLESHOOTING #28.
 */
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy repairThenMigrate() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
