package com.expansion.server.domain.palette.service;

import com.expansion.server.domain.common.entity.Like;
import com.expansion.server.domain.common.repository.LikeRepository;
import com.expansion.server.domain.palette.dto.PaletteRequest;
import com.expansion.server.domain.palette.dto.PaletteResponse;
import com.expansion.server.domain.palette.dto.PaletteSummaryResponse;
import com.expansion.server.domain.palette.entity.Palette;
import com.expansion.server.domain.palette.repository.PaletteRepository;
import com.expansion.server.domain.user.entity.Profile;
import com.expansion.server.domain.user.entity.User;
import com.expansion.server.domain.user.repository.ProfileRepository;
import com.expansion.server.domain.user.repository.UserRepository;
import com.expansion.server.domain.user.service.EmailVerificationGuard;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaletteService {

    static final String LIKE_TARGET = "PALETTE";
    static final int MIN_COLORS = 2;
    static final int MAX_COLORS = 256;
    private static final int MAX_PAGE_SIZE = 60;
    private static final TypeReference<List<String>> COLOR_LIST = new TypeReference<>() {};

    private final PaletteRepository paletteRepository;
    private final LikeRepository likeRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final ObjectMapper objectMapper;

    // ── 목록 ────────────────────────────────────────────────
    /** sort = popular(좋아요순, 같으면 최신) | 그 외(최신순). 모든 필터는 선택 */
    public Page<PaletteSummaryResponse> search(String sort, Integer minColors, Integer maxColors,
                                               String keyword, Long authorId, int page, int size) {
        // 마지막 기준 paletteId — 같은 값(기본 팔레트는 등록 시각까지 같음)끼리 순서를 고정해야
        // 페이지를 넘길 때 같은 팔레트가 두 번 나오거나 빠지지 않는다
        Sort order = "popular".equals(sort)
                ? Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("createdAt"), Sort.Order.desc("paletteId"))
                : Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("paletteId"));
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), order);

        String pattern = (keyword == null || keyword.isBlank())
                ? null : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
        Page<Palette> result = paletteRepository.search(authorId, minColors, maxColors, pattern, pageable);

        Map<Long, Profile> authors = authorProfiles(result.getContent());
        return result.map(p -> PaletteSummaryResponse.of(p, parseColors(p), authorOf(p, authors)));
    }

    // ── 상세 ────────────────────────────────────────────────
    public PaletteResponse get(Long paletteId, Long currentUserId) {
        Palette p = findActive(paletteId);
        boolean liked = currentUserId != null
                && likeRepository.existsByUser_UserIdAndTargetIdAndTargetType(currentUserId, paletteId, LIKE_TARGET);
        return toResponse(p, liked, currentUserId);
    }

    // ── 등록·수정·삭제 ──────────────────────────────────────
    @Transactional
    public PaletteResponse create(Long userId, PaletteRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        EmailVerificationGuard.assertVerified(user);   // 소프트 게이트 — 콘텐츠 생성은 이메일 인증 후

        List<String> colors = normalizeColors(request.getColors());
        Palette p = paletteRepository.save(Palette.builder()
                .user(user)
                .name(request.getName().trim())
                .description(blankToNull(request.getDescription()))
                .colors(toJson(colors))
                .colorCount(colors.size())
                .build());
        return toResponse(p, false, userId);
    }

    @Transactional
    public PaletteResponse update(Long userId, Long paletteId, PaletteRequest request) {
        Palette p = findActive(paletteId);
        requireOwner(p, userId);
        List<String> colors = normalizeColors(request.getColors());
        p.update(request.getName().trim(), blankToNull(request.getDescription()), toJson(colors), colors.size());
        boolean liked = likeRepository.existsByUser_UserIdAndTargetIdAndTargetType(userId, paletteId, LIKE_TARGET);
        return toResponse(p, liked, userId);
    }

    /** 삭제 = 숨김(status DELETED). 공식 팔레트는 작성자가 없어 누구도 못 지움 */
    @Transactional
    public void delete(Long userId, Long paletteId) {
        Palette p = findActive(paletteId);
        requireOwner(p, userId);
        p.delete();
    }

    // ── 좋아요 ──────────────────────────────────────────────
    /** 토글 — 반환 = 토글 후 좋아요 상태. 개수는 원자적 UPDATE(동시 토글 lost update 방지) */
    @Transactional
    public boolean toggleLike(Long userId, Long paletteId) {
        findActive(paletteId);
        var existing = likeRepository.findByUser_UserIdAndTargetIdAndTargetType(userId, paletteId, LIKE_TARGET);
        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
            paletteRepository.addLikeCount(paletteId, -1);
            return false;
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        likeRepository.save(Like.builder().user(user).targetId(paletteId).targetType(LIKE_TARGET).build());
        paletteRepository.addLikeCount(paletteId, 1);
        return true;
    }

    // ── 내부 ────────────────────────────────────────────────
    private Palette findActive(Long paletteId) {
        Palette p = paletteRepository.findById(paletteId)
                .orElseThrow(() -> new CustomException(ErrorCode.PALETTE_NOT_FOUND));
        if (!p.isActive()) throw new CustomException(ErrorCode.PALETTE_NOT_FOUND);
        return p;
    }

    /** 작성자 본인만 — 공식 팔레트(작성자 없음)는 누구도 수정·삭제 불가 */
    private static void requireOwner(Palette p, Long userId) {
        if (!p.isOwnedBy(userId)) throw new CustomException(ErrorCode.ACCESS_DENIED);
    }

    /** '#rrggbb' 소문자로 통일 + 순서 유지 중복 제거. 결과가 2~256개가 아니면 400 */
    static List<String> normalizeColors(List<String> raw) {
        Set<String> set = new LinkedHashSet<>();
        for (String c : raw) {
            String hex = c.trim().toLowerCase(Locale.ROOT);
            set.add(hex.startsWith("#") ? hex : "#" + hex);
        }
        if (set.size() < MIN_COLORS || set.size() > MAX_COLORS) {
            throw new CustomException(ErrorCode.INVALID_PALETTE_COLORS);
        }
        return List.copyOf(set);
    }

    private PaletteResponse toResponse(Palette p, boolean liked, Long currentUserId) {
        Profile author = p.isOfficial() ? null
                : profileRepository.findByUser_UserId(p.getUser().getUserId()).orElse(null);
        return PaletteResponse.of(p, parseColors(p), author, liked, p.isOwnedBy(currentUserId));
    }

    /** 목록의 작성자 프로필 배치 조회(N+1 방지) — 공식 팔레트는 제외 */
    private Map<Long, Profile> authorProfiles(List<Palette> palettes) {
        List<Long> ids = palettes.stream()
                .filter(p -> !p.isOfficial())
                .map(p -> p.getUser().getUserId())
                .distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return profileRepository.findAllByUser_UserIdIn(ids).stream()
                .collect(Collectors.toMap(pr -> pr.getUser().getUserId(), pr -> pr));
    }

    private static Profile authorOf(Palette p, Map<Long, Profile> authors) {
        return p.isOfficial() ? null : authors.get(p.getUser().getUserId());
    }

    private List<String> parseColors(Palette p) {
        try {
            return objectMapper.readValue(p.getColors(), COLOR_LIST);
        } catch (RuntimeException e) {
            return List.of();   // 깨진 데이터는 빈 목록으로(화면이 죽지 않게)
        }
    }

    private String toJson(List<String> colors) {
        return objectMapper.writeValueAsString(colors);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
