package com.expansion.server.domain.commission.service;

import com.expansion.server.domain.commission.dto.*;
import com.expansion.server.domain.commission.entity.ArtistService;
import com.expansion.server.domain.commission.repository.ArtistServiceRepository;
import com.expansion.server.domain.user.entity.Profile;
import com.expansion.server.domain.user.entity.User;
import com.expansion.server.domain.user.repository.ProfileRepository;
import com.expansion.server.domain.user.repository.UserRepository;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArtistServiceService {

    private final ArtistServiceRepository artistServiceRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    // 공개 목록 (OPEN 상태) — category/keyword 선택 필터
    public Page<ArtistServiceSummary> getOpenList(String category, String keyword, Pageable pageable) {
        String normalizedCategory = (category != null && !category.isBlank() && !"전체".equals(category)) ? category : null;
        String normalizedKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return artistServiceRepository.search("OPEN", normalizedCategory, normalizedKeyword, pageable)
                .map(service -> {
                    Profile profile = profileRepository.findByUser_UserId(service.getArtist().getUserId()).orElse(null);
                    return ArtistServiceSummary.of(service, profile);
                });
    }

    // 내 서비스 목록
    public Page<ArtistServiceSummary> getMyList(Long artistId, Pageable pageable) {
        return artistServiceRepository.findByArtist_UserId(artistId, pageable)
                .map(service -> {
                    Profile profile = profileRepository.findByUser_UserId(artistId).orElse(null);
                    return ArtistServiceSummary.of(service, profile);
                });
    }

    // 상세
    public ArtistServiceResponse getService(Long serviceId) {
        ArtistService service = findById(serviceId);
        Profile profile = profileRepository.findByUser_UserId(service.getArtist().getUserId()).orElse(null);
        return ArtistServiceResponse.of(service, profile);
    }

    // 등록
    @Transactional
    public ArtistServiceResponse create(Long artistId, ArtistServiceCreateRequest request) {
        User artist = userRepository.findById(artistId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        ArtistService service = ArtistService.builder()
                .artist(artist)
                .title(request.getTitle())
                .description(request.getDescription())
                .serviceType(request.getServiceType())
                .basePrice(request.getBasePrice())
                .priceMin(request.getPriceMin())
                .priceMax(request.getPriceMax())
                .estimatedDays(request.getEstimatedDays())
                .category(request.getCategory() != null && !request.getCategory().isBlank()
                        ? request.getCategory() : "기타")
                .build();

        artistServiceRepository.save(service);

        Profile profile = profileRepository.findByUser_UserId(artistId).orElse(null);
        return ArtistServiceResponse.of(service, profile);
    }

    // 수정
    @Transactional
    public ArtistServiceResponse update(Long artistId, Long serviceId, ArtistServiceUpdateRequest request) {
        ArtistService service = findById(serviceId);

        if (!service.getArtist().getUserId().equals(artistId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        service.update(request.getTitle(), request.getDescription(), request.getServiceType(),
                request.getBasePrice(), request.getPriceMin(), request.getPriceMax(),
                request.getEstimatedDays(), request.getCategory());

        Profile profile = profileRepository.findByUser_UserId(artistId).orElse(null);
        return ArtistServiceResponse.of(service, profile);
    }

    // 마감 처리
    @Transactional
    public void close(Long artistId, Long serviceId) {
        ArtistService service = findById(serviceId);

        if (!service.getArtist().getUserId().equals(artistId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        service.close();
    }

    // 삭제
    @Transactional
    public void delete(Long artistId, Long serviceId) {
        ArtistService service = findById(serviceId);

        if (!service.getArtist().getUserId().equals(artistId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        artistServiceRepository.delete(service);
    }

    private ArtistService findById(Long serviceId) {
        return artistServiceRepository.findById(serviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
    }
}
