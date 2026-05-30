package com.expansion.server.domain.common.controller;

import com.expansion.server.domain.common.dto.TagResponse;
import com.expansion.server.domain.common.service.TagService;
import com.expansion.server.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    /**
     * 인기 태그 TOP 20 조회
     * GET /api/tags
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<TagResponse>>> getTopTags() {
        return ResponseEntity.ok(ApiResponse.success(tagService.getTopTags()));
    }

    /**
     * 태그 자동완성 검색
     * GET /api/tags/search?keyword=캐
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<TagResponse>>> searchTags(@RequestParam String keyword) {
        return ResponseEntity.ok(ApiResponse.success(tagService.searchTags(keyword)));
    }
}
