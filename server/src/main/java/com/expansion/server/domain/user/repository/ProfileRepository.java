package com.expansion.server.domain.user.repository;

import com.expansion.server.domain.user.entity.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

    Optional<Profile> findByUser_UserId(Long userId);

    boolean existsByNickname(String nickname);

    Optional<Profile> findByNickname(String nickname);

    List<Profile> findAllByUser_UserIdIn(Collection<Long> userIds);

    /**
     * 활성 계정 중 팔로워 많은 순(메인 인기 작가가 모자랄 때 채우는 용도) — id로 순서 고정.
     * 프로필 공개 여부(is_public)는 조건에서 뺌 — 화면에서 스위치를 없앴고 다른 곳엔 적용되지 않던 값이라,
     * 예전에 비공개로 바꿔 둔 사람만 계속 빠지던 문제. 탈퇴 계정은 status로 제외
     */
    @Query("""
            SELECT p FROM Profile p JOIN FETCH p.user u
            WHERE u.status = 'ACTIVE'
            ORDER BY p.followerCount DESC, u.userId ASC
            """)
    List<Profile> findTopActiveByFollowers(Pageable pageable);
}
