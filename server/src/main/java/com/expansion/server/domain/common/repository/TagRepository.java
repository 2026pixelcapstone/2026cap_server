package com.expansion.server.domain.common.repository;

import com.expansion.server.domain.common.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByTagName(String tagName);

    boolean existsByTagName(String tagName);

    List<Tag> findTop20ByOrderByPostCountDesc();

    // 자동완성 — 키워드 포함 태그를 사용 빈도순으로 최대 10개
    List<Tag> findTop10ByTagNameContainingIgnoreCaseOrderByPostCountDesc(String keyword);
}
