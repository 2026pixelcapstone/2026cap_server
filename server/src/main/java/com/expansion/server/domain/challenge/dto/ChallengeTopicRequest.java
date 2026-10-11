package com.expansion.server.domain.challenge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 관리자 주제 추가·수정 — 길이·중복 상세 검사는 서비스(2~10자, 설명 100자) */
public record ChallengeTopicRequest(
        @NotBlank @Size(max = 50) String topic,
        @Size(max = 300) String description
) {}
