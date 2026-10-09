package com.expansion.server.domain.challenge.service;

import com.expansion.server.domain.challenge.entity.Challenge;
import com.expansion.server.domain.challenge.entity.ChallengeEntry;
import com.expansion.server.domain.challenge.repository.ChallengeEntryRepository;
import com.expansion.server.domain.challenge.repository.ChallengeRepository;
import com.expansion.server.domain.gallery.entity.GalleryPost;
import com.expansion.server.domain.gallery.entity.Visibility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 챌린지 참가 — 갤러리 업로드 체크박스와 작품 상세 버튼이 함께 쓰는 단일 경로.
 * GalleryService가 이 빈을 쓰므로 여기서는 GalleryService를 참조하지 않는다(순환 의존 방지).
 * 예외를 던지지 않고 결과 값을 돌려준다 — 작품 등록 트랜잭션을 rollback-only로 만들지 않기 위함.
 */
@Service
@RequiredArgsConstructor
public class ChallengeEntryService {

    private final ChallengeRepository challengeRepository;
    private final ChallengeEntryRepository challengeEntryRepository;

    @Transactional
    public ChallengeEntryResult enter(Long userId, GalleryPost post) {
        Optional<Challenge> current = challengeRepository.findByStartsAt(ChallengeWeek.current().startsAt());
        if (current.isEmpty()) return ChallengeEntryResult.NO_CHALLENGE;
        Challenge challenge = current.get();

        if (!post.getUser().getUserId().equals(userId)) return ChallengeEntryResult.NOT_OWNER;
        if (post.getOriginPost() != null) return ChallengeEntryResult.REMIX;
        if (post.getVisibility() != Visibility.PUBLIC) return ChallengeEntryResult.NOT_PUBLIC;
        LocalDateTime createdAt = post.getCreatedAt();
        if (createdAt.isBefore(challenge.getStartsAt()) || !createdAt.isBefore(challenge.getEndsAt())) {
            return ChallengeEntryResult.OUT_OF_PERIOD;
        }

        boolean hadOther = challengeEntryRepository.findByChallengeIdAndUserId(challenge.getChallengeId(), userId)
                .map(ChallengeEntry::getPostId)
                .filter(prev -> !prev.equals(post.getPostId()))
                .isPresent();

        challengeEntryRepository.upsert(challenge.getChallengeId(), userId, post.getPostId(), LocalDateTime.now());
        return hadOther ? ChallengeEntryResult.REPLACED : ChallengeEntryResult.ENTERED;
    }
}
