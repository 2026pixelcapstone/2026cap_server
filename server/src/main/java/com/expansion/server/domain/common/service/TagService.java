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
}
