package com.expansion.server.domain.challenge.service;

import com.expansion.server.domain.ai.client.GeminiClient;
import com.expansion.server.domain.ai.client.GeminiClient.TopicIdea;
import com.expansion.server.domain.challenge.dto.ChallengeTopicResponse;
import com.expansion.server.domain.challenge.entity.ChallengeTopic;
import com.expansion.server.domain.challenge.repository.ChallengeTopicRepository;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 챌린지 주제 풀 관리 — AI 자동 보충(C-2b) + 관리자 관리(목록·추가·수정·사용 중지).
 * 관리자 API는 관리자 화면이 생기면 바로 쓰도록 미리 준비(/api/admin/** = ADMIN만).
 */
@Slf4j
@Service
public class ChallengeTopicService {

    static final int TOPIC_MIN = 2;
    static final int TOPIC_MAX = 10;
    static final int DESCRIPTION_MAX = 100;
    /** V39 주제 이름 유니크 인덱스 — 관리자 추가·수정 경합 시 이 제약 위반만 409로 바꾼다 */
    static final String TOPIC_UNIQUE_INDEX = "uq_challenge_topics_topic_norm";
    /** AI 출력 1차 거름망(프롬프트 금지 조건의 보조). 공개 전 관리자 수정 기간이 최종 안전장치 */
    private static final List<String> BANNED_WORDS = List.of(
            "살인", "자살", "시체", "학살", "고문", "마약", "섹스", "야한", "성인", "음란", "도박", "테러");

    private final ChallengeTopicRepository topicRepository;
    private final GeminiClient geminiClient;
    private final TransactionTemplate transactionTemplate;
    private final int minUnused;
    private final int generateCount;
    private final int recentWeeks;

    public ChallengeTopicService(
            ChallengeTopicRepository topicRepository,
            GeminiClient geminiClient,
            TransactionTemplate transactionTemplate,
            @Value("${challenge.topic-pool.min-unused:4}") int minUnused,
            @Value("${challenge.topic-pool.generate-count:8}") int generateCount,
            @Value("${challenge.topic-pool.recent-weeks:12}") int recentWeeks) {
        this.topicRepository = topicRepository;
        this.geminiClient = geminiClient;
        this.transactionTemplate = transactionTemplate;
        this.minUnused = minUnused;
        this.generateCount = generateCount;
        this.recentWeeks = recentWeeks;
    }

    // ── AI 보충 ──────────────────────────────────────────────

    /** 안 쓴 ACTIVE 주제가 기준 미만일 때만 보충. 스케줄러(ChallengeRollover)에서 호출 */
    public int replenishIfLow() {
        long unused = topicRepository.countByUsedAtIsNullAndStatus(ChallengeTopic.STATUS_ACTIVE);
        if (unused >= minUnused) return 0;
        return replenish(unused);
    }

    /** 관리자 '지금 보충' — 기준과 상관없이 한 번 생성 */
    public int replenishNow() {
        return replenish(topicRepository.countByUsedAtIsNullAndStatus(ChallengeTopic.STATUS_ACTIVE));
    }

    /**
     * Gemini 호출은 트랜잭션 밖에서(외부 HTTP 동안 DB 연결을 잡지 않도록), 저장만 짧은 트랜잭션으로.
     * AI 실패는 0을 돌려주고 로그만 — 챌린지 생성은 기존 풀(재사용 포함)로 계속 돈다.
     */
    private int replenish(long unusedActive) {
        List<String> recent = topicRepository
                .findByUsedAtIsNotNullOrderByUsedAtDesc(PageRequest.of(0, recentWeeks))
                .stream().map(ChallengeTopic::getTopic).toList();
        List<String> upcoming = topicRepository.findByUsedAtIsNullOrderBySortOrderAscTopicIdAsc()
                .stream().map(ChallengeTopic::getTopic).toList();
        // 새 주제가 쓰일 즈음의 달(남은 예정 주제 수만큼 뒤의 주) — 계절감용
        int month = Instant.now().plus((unusedActive + 1) * 7, ChronoUnit.DAYS)
                .atZone(ChallengeWeek.KST).getMonthValue();

        List<TopicIdea> ideas;
        try {
            ideas = geminiClient.suggestChallengeTopics(generateCount, recent, upcoming, month);
        } catch (Exception e) {
            log.warn("[CHALLENGE] AI 주제 생성 실패 — 기존 풀로 계속: {}", e.getMessage());
            return 0;
        }

        List<TopicIdea> valid = filterValid(ideas);
        Integer inserted = transactionTemplate.execute(status -> {
            int next = topicRepository.findMaxSortOrder() + 1;
            int count = 0;
            LocalDateTime now = LocalDateTime.now();
            for (TopicIdea idea : valid) {
                int rows = topicRepository.insertIfAbsent(
                        idea.topic(), idea.description(), next, ChallengeTopic.SOURCE_AI, now);
                if (rows > 0) { count++; next++; }
            }
            return count;
        });
        int added = inserted == null ? 0 : inserted;
        log.info("[CHALLENGE] AI 주제 보충 — 받은 {}개, 검사 통과 {}개, 추가 {}개(나머지는 중복)",
                ideas.size(), valid.size(), added);
        return added;
    }

    /** 길이·금지어·응답 안 중복 검사(같은 이름 판정은 DB 유니크와 같은 정규화) */
    List<TopicIdea> filterValid(List<TopicIdea> ideas) {
        Set<String> seen = new HashSet<>();
        List<TopicIdea> valid = new ArrayList<>();
        for (TopicIdea idea : ideas) {
            String topic = idea.topic() == null ? "" : idea.topic().trim();
            String description = idea.description() == null ? "" : idea.description().trim();
            if (!isValid(topic, description)) {
                log.info("[CHALLENGE] AI 주제 제외(형식·금지어): {}", topic);
                continue;
            }
            if (seen.add(normalize(topic))) {
                valid.add(new TopicIdea(topic, description));
            }
        }
        return valid;
    }

    private static boolean isValid(String topic, String description) {
        int len = topic.codePointCount(0, topic.length());
        if (len < TOPIC_MIN || len > TOPIC_MAX) return false;
        if (description.isEmpty() || description.length() > DESCRIPTION_MAX) return false;
        if (topic.contains("\n") || description.contains("\n")) return false;
        String joined = topic + " " + description;
        return BANNED_WORDS.stream().noneMatch(joined::contains);
    }

    /** V39 유니크 인덱스 lower(regexp_replace(topic, '\s', '', 'g'))와 같은 규칙 */
    static String normalize(String topic) {
        return topic.replaceAll("\\s", "").toLowerCase(Locale.ROOT);
    }

    // ── 관리자 ──────────────────────────────────────────────

    /** 목록 — 공개 예정 순서. status: ''|ACTIVE|DISABLED, source: ''|SEED|AI|ADMIN, used: all|used|unused */
    @Transactional(readOnly = true)
    public Page<ChallengeTopicResponse> search(String status, String source, String used, int page, int size) {
        return topicRepository.searchForAdmin(
                        orEmpty(status), orEmpty(source), used == null || used.isBlank() ? "all" : used,
                        PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100))))
                .map(ChallengeTopicResponse::of);
    }

    @Transactional
    public ChallengeTopicResponse create(String topic, String description) {
        String t = trimToEmpty(topic);
        String d = trimToEmpty(description);
        validateForAdmin(t, d, 0L);
        // 확인(existsNormalized)과 저장 사이에 같은 이름이 먼저 들어오면(AI 보충·다른 관리자 요청) 유니크에 걸림
        // → 그 자리에서 flush해 위반을 잡고 409로 바꾼다(커밋 시점까지 미루면 500)
        try {
            ChallengeTopic saved = topicRepository.saveAndFlush(new ChallengeTopic(
                    t, d.isEmpty() ? null : d, topicRepository.findMaxSortOrder() + 1, ChallengeTopic.SOURCE_ADMIN));
            return ChallengeTopicResponse.of(saved);
        } catch (DataIntegrityViolationException e) {
            throw translateDuplicate(e);
        }
    }

    /** 문구 수정 — 이미 연 챌린지는 복사본이라 영향 없음 */
    @Transactional
    public ChallengeTopicResponse update(Long topicId, String topic, String description) {
        ChallengeTopic entity = topicRepository.findById(topicId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_TOPIC_NOT_FOUND));
        String t = trimToEmpty(topic);
        String d = trimToEmpty(description);
        validateForAdmin(t, d, topicId);
        entity.edit(t, d.isEmpty() ? null : d);
        try {
            topicRepository.flush();   // create와 같은 이유로 즉시 반영해 유니크 위반을 여기서 잡음
        } catch (DataIntegrityViolationException e) {
            throw translateDuplicate(e);
        }
        return ChallengeTopicResponse.of(entity);
    }

    /** 사용 중지·재사용 — 행은 남겨 중복 방지에 계속 쓰임 */
    @Transactional
    public ChallengeTopicResponse changeStatus(Long topicId, String status) {
        ChallengeTopic entity = topicRepository.findById(topicId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_TOPIC_NOT_FOUND));
        entity.changeStatus(status);
        return ChallengeTopicResponse.of(entity);
    }

    private void validateForAdmin(String topic, String description, long excludeId) {
        int len = topic.codePointCount(0, topic.length());
        if (len < TOPIC_MIN || len > TOPIC_MAX || description.length() > DESCRIPTION_MAX) {
            throw new CustomException(ErrorCode.INVALID_CHALLENGE_TOPIC);
        }
        if (topicRepository.existsNormalized(normalize(topic), excludeId)) {
            throw new CustomException(ErrorCode.CHALLENGE_TOPIC_DUPLICATE);
        }
    }

    /**
     * 주제 이름 유니크 위반만 409로 변환하고, 그 밖의 무결성 오류는 그대로 다시 던진다(엉뚱한 409로 감추지 않음).
     * 예외 감싸기 단계가 환경마다 다를 수 있어 원인 체인 전체에서 제약 이름을 찾는다.
     */
    private RuntimeException translateDuplicate(DataIntegrityViolationException e) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof ConstraintViolationException cve
                    && TOPIC_UNIQUE_INDEX.equalsIgnoreCase(cve.getConstraintName())) {
                return new CustomException(ErrorCode.CHALLENGE_TOPIC_DUPLICATE);
            }
        }
        return e;
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
