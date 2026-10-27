package com.expansion.server.domain.ai.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 명암 제안 요청 (에디터 AI 탭 — 자체 모델).
 *
 * @param imageBase64 현재 프레임을 PNG로 뽑은 base64. data URL 접두어는 있어도 됨(서비스에서 제거).
 *                    평면 그림(기본색 + 외곽선), 64×64 이하. 64×64 PNG는 수 KB라 상한은 넉넉히 20만 자.
 * @param angle       광원 각도(°). 0 이상 360 미만, 135 = 왼쪽 위(기본), 90 = 위, 45 = 오른쪽 위. 없으면 135.
 */
public record ShadeSuggestRequest(
        @NotBlank(message = "이미지가 필요합니다.")
        @Size(max = 200_000, message = "이미지가 너무 큽니다.")
        String imageBase64,

        @DecimalMin(value = "0", message = "광원 각도는 0 이상이어야 합니다.")
        @DecimalMax(value = "360", inclusive = false, message = "광원 각도는 360 미만이어야 합니다.")
        Double angle
) {
}
