package com.expansion.server.domain.asset.dto;

import com.expansion.server.domain.common.entity.Category;

/** 카테고리 선택지(업로드 드롭다운/필터). */
public record CategoryResponse(Long categoryId, String name) {
    public static CategoryResponse of(Category c) {
        return new CategoryResponse(c.getCategoryId(), c.getName());
    }
}
