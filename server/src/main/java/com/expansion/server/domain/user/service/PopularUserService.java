package com.expansion.server.domain.user.service;

import com.expansion.server.domain.gallery.repository.GalleryPostRepository;
import com.expansion.server.domain.user.dto.PopularUserResponse;
import com.expansion.server.domain.user.entity.Profile;
import com.expansion.server.domain.user.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 메인 '인기 작가' — 최근 days일 동안 공개 작품으로 좋아요를 많이 받은 작가.
 * 모자라면 팔로워 많은 공개 작가로 채운다. 탈퇴·정지·비공개 프로필 제외.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PopularUserService {

    private final GalleryPostRepository galleryPostRepository;
    private final ProfileRepository profileRepository;

    public List<PopularUserResponse> getPopular(int days, int size) {
        int limit = Math.max(1, Math.min(size, 24));
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(1, Math.min(days, 30)));

        // [user_id, 받은 좋아요 수] — 순서 유지
        Map<Long, Long> recent = new LinkedHashMap<>();
        for (Object[] row : galleryPostRepository.findPopularAuthors(since, limit)) {
            recent.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        Map<Long, Profile> profiles = profileRepository.findAllByUser_UserIdIn(recent.keySet()).stream()
                .collect(Collectors.toMap(p -> p.getUser().getUserId(), Function.identity()));

        List<PopularUserResponse> result = new ArrayList<>();
        recent.forEach((userId, likes) -> {
            Profile p = profiles.get(userId);
            if (p != null) result.add(PopularUserResponse.of(p, likes));
        });

        if (result.size() < limit) {
            for (Profile p : profileRepository.findTopPublicByFollowers(PageRequest.of(0, limit + recent.size()))) {
                if (result.size() >= limit) break;
                if (!recent.containsKey(p.getUser().getUserId())) result.add(PopularUserResponse.of(p, 0));
            }
        }
        return result;
    }
}
