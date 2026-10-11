package com.expansion.server.domain.challenge.service;

import com.expansion.server.domain.challenge.dto.ChallengeEntryResponse;
import com.expansion.server.domain.challenge.dto.ChallengeResponse;
import com.expansion.server.domain.challenge.dto.PastChallengeResponse;
import com.expansion.server.domain.challenge.entity.Challenge;
import com.expansion.server.domain.challenge.entity.ChallengeEntry;
import com.expansion.server.domain.challenge.repository.ChallengeEntryRepository;
import com.expansion.server.domain.challenge.repository.ChallengeRepository;
import com.expansion.server.domain.gallery.dto.GalleryPostSummary;
import com.expansion.server.domain.gallery.entity.GalleryPost;
import com.expansion.server.domain.gallery.entity.Visibility;
import com.expansion.server.domain.gallery.repository.GalleryPostRepository;
import com.expansion.server.domain.gallery.service.GalleryService;
import com.expansion.server.domain.user.entity.User;
import com.expansion.server.domain.user.repository.UserRepository;
import com.expansion.server.domain.user.service.EmailVerificationGuard;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 챌린지 조회·참가 API. 생성·판정은 ChallengeRollover/ChallengeJudge 담당(조회는 읽기만). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChallengeService {

    private static final int MAX_PAGE_SIZE = 48;

    private final ChallengeRepository challengeRepository;
    private final ChallengeEntryRepository challengeEntryRepository;
    private final ChallengeEntryService challengeEntryService;
    private final GalleryPostRepository galleryPostRepository;
    private final GalleryService galleryService;
    private final UserRepository userRepository;

    /** 이번 주 챌린지 — 아직 생성 전(준비 중)이면 null */
    public ChallengeResponse getCurrent(Long userId) {
        return findCurrent().map(c -> new ChallengeResponse(
                c.getChallengeId(), c.getTopic(), c.getDescription(),
                toKst(c.getStartsAt()), toKst(c.getEndsAt()),
                challengeEntryRepository.countPublicEntries(c.getChallengeId()),
                userId == null ? null : challengeEntryRepository
                        .findByChallengeIdAndUserId(c.getChallengeId(), userId)
                        .map(ChallengeEntry::getPostId).orElse(null)
        )).orElse(null);
    }

    /** 참가작(공개만) — sort=likes(현재 좋아요순) | recent(최신순) */
    public Page<GalleryPostSummary> getEntries(Long challengeId, String sort, int page, int size) {
        if (!challengeRepository.existsById(challengeId)) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, MAX_PAGE_SIZE)));
        Page<Long> ids = "recent".equals(sort)
                ? challengeEntryRepository.findPublicPostIdsByRecent(challengeId, pageable)
                : challengeEntryRepository.findPublicPostIdsByLikes(challengeId, pageable);

        Map<Long, GalleryPost> byId = galleryPostRepository.findAllById(ids.getContent()).stream()
                .collect(Collectors.toMap(GalleryPost::getPostId, Function.identity()));
        List<GalleryPost> ordered = ids.getContent().stream().map(byId::get).filter(Objects::nonNull).toList();
        return new PageImpl<>(galleryService.toSummaryList(ordered), pageable, ids.getTotalElements());
    }

    /** 판정 끝난 지난 챌린지 + 톱3(삭제·비공개 우승작은 post=null) */
    public Page<PastChallengeResponse> getPast(int page, int size) {
        Page<Challenge> challenges = challengeRepository.findByJudgedAtIsNotNullOrderByStartsAtDesc(
                PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 20))));
        List<Long> challengeIds = challenges.getContent().stream().map(Challenge::getChallengeId).toList();

        List<ChallengeEntry> ranked = challengeIds.isEmpty() ? List.of()
                : challengeEntryRepository.findByChallengeIdInAndFinalRankIsNotNull(challengeIds);

        List<GalleryPost> visiblePosts = galleryPostRepository.findAllById(
                        ranked.stream().map(ChallengeEntry::getPostId).toList()).stream()
                .filter(p -> p.getVisibility() == Visibility.PUBLIC)
                .toList();
        Map<Long, GalleryPostSummary> summaries = galleryService.toSummaryList(visiblePosts).stream()
                .collect(Collectors.toMap(GalleryPostSummary::getPostId, Function.identity()));

        // challengeId → (rank → entry)
        Map<Long, Map<Integer, ChallengeEntry>> byChallenge = ranked.stream().collect(Collectors.groupingBy(
                ChallengeEntry::getChallengeId,
                Collectors.toMap(ChallengeEntry::getFinalRank, Function.identity())));

        return challenges.map(c -> {
            Map<Integer, ChallengeEntry> ranks = byChallenge.getOrDefault(c.getChallengeId(), Map.of());
            List<PastChallengeResponse.Winner> winners = new ArrayList<>();
            for (int rank = 1; rank <= c.getWinnerCount(); rank++) {
                ChallengeEntry e = ranks.get(rank);   // 작품이 삭제되면 참가 행도 없어짐(CASCADE)
                winners.add(new PastChallengeResponse.Winner(
                        rank,
                        e != null && e.getFinalLikeCount() != null ? e.getFinalLikeCount() : 0,
                        e != null ? summaries.get(e.getPostId()) : null));
            }
            return new PastChallengeResponse(c.getChallengeId(), c.getTopic(), c.getDescription(),
                    toKst(c.getStartsAt()), toKst(c.getEndsAt()), winners);
        });
    }

    /** 작품 상세 버튼 — 참가 또는 교체 */
    @Transactional
    public ChallengeEntryResponse enter(Long userId, Long postId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        EmailVerificationGuard.assertVerified(user);
        GalleryPost post = galleryPostRepository.findById(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        ChallengeEntryResult result = challengeEntryService.enter(userId, post);
        if (!result.isSuccess()) {
            throw new CustomException(result.getError());
        }
        return new ChallengeEntryResponse(result.name(), postId);
    }

    /** 참가 취소 */
    @Transactional
    public void cancel(Long userId) {
        Challenge current = findCurrent().orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_OPEN));
        if (challengeEntryRepository.deleteByChallengeIdAndUserId(current.getChallengeId(), userId) == 0) {
            throw new CustomException(ErrorCode.CHALLENGE_ENTRY_NOT_FOUND);
        }
    }

    private Optional<Challenge> findCurrent() {
        return challengeRepository.findByStartsAt(ChallengeWeek.current().startsAt());
    }

    private static OffsetDateTime toKst(LocalDateTime serverTime) {
        return serverTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(ChallengeWeek.KST).toOffsetDateTime();
    }
}
