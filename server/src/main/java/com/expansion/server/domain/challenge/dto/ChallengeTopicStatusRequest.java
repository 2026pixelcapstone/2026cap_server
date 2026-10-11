package com.expansion.server.domain.challenge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 관리자 주제 사용 중지·재사용 */
public record ChallengeTopicStatusRequest(
        @NotBlank @Pattern(regexp = "ACTIVE|DISABLED", message = "status는 ACTIVE 또는 DISABLED만 허용됩니다.")
        String status
) {}
