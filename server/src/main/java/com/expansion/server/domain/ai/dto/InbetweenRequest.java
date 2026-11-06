package com.expansion.server.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 중간 동작 제안 요청 (에디터 AI 탭 — 자체 모델).
 *
 * @param imageA 앞 키프레임 PNG base64(data URL 접두어 허용), 128×128 이하
 * @param imageC 뒤 키프레임 PNG base64, A와 같은 캔버스 크기
 */
public record InbetweenRequest(
        @NotBlank(message = "앞 프레임 이미지가 필요합니다.")
        @Size(max = 200_000, message = "이미지가 너무 큽니다.")
        String imageA,

        @NotBlank(message = "뒤 프레임 이미지가 필요합니다.")
        @Size(max = 200_000, message = "이미지가 너무 큽니다.")
        String imageC
) {
}
