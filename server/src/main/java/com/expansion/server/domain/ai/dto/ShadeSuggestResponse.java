package com.expansion.server.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * 명암 제안 응답 — 내부 파이썬 서비스(ml/serve) 응답을 검증한 뒤 그대로 전달.
 *
 * @param levels    행 우선 height × width. 재질 칸은 단계 −2~+2, 외곽선·투명은 null.
 * @param materials 재질(평면 색)마다 기준색·칸 수·쓴 단계·색 계열(ramp: "-2"~"2" → #rrggbb).
 *                  화면은 칸마다 그 칸 기준색의 ramp[단계]로 칠해 명암 레이어를 만든다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShadeSuggestResponse(
        int width,
        int height,
        double angle,
        int outlinePixels,
        List<List<Integer>> levels,
        List<Material> materials,
        String model
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Material(
            String base,
            int pixels,
            List<Integer> used,
            Map<String, String> ramp
    ) {
    }
}
