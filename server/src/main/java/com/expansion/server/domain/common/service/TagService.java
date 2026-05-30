package com.expansion.server.domain.common.service;

import com.expansion.server.domain.common.dto.TagResponse;
import com.expansion.server.domain.common.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TagService {

    private final TagRepository tagRepository;

    /** 사용 빈도 상위 태그 목록 조회 */
    public List<TagResponse> getTopTags() {
        return tagRepository.findTop20ByOrderByPostCountDesc()
                .stream()
                .map(TagResponse::from)
                .toList();
    }

    /** 키워드 자동완성 — 기존 태그 중 일치 후보 조회 */
    public List<TagResponse> searchTags(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        return tagRepository
                .findTop10ByTagNameContainingIgnoreCaseOrderByPostCountDesc(keyword.trim())
                .stream()
                .map(TagResponse::from)
                .toList();
    }
}
