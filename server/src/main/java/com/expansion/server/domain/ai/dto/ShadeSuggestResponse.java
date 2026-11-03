package com.expansion.server.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * 명암 제안 응답 — 내부 파이썬 서비스(ml/serve) 응답을 검증한 뒤 그대로 전달.
 *
 * @param levels           기본 강도의 결과, 행 우선 height × width. 재질 칸은 단계 −2~+2, 외곽선·투명은 null.
 * @param materials        재질(평면 색)마다 기준색·칸 수·쓴 단계·색 계열(ramp: "-2"~"2" → #rrggbb).
 *                         화면은 칸마다 그 칸 기준색의 ramp[단계]로 칠해 명암 레이어를 만든다.
 * @param version          계산 방식("v2" — 부풀리기 배치 + 강도, ml/serve/README.md).
 * @param strengths        한 번에 계산한 강도 목록(예: 0.3·0.5·0.7·1.0).
 * @param defaultStrength  levels가 담은 강도.
 * @param levelsByStrength 강도마다 단계 문자열(행 우선, 길이 width × height, '0'~'4' = −2~+2, '.' = 대상 아님).
 *                         키는 strengths 값을 %g로 쓴 것("1" 주의). 화면 강도 슬라이더가 다시 요청하지 않게.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShadeSuggestResponse(
        int width,
        int height,
        double angle,
        int outlinePixels,
        List<List<Integer>> levels,
        List<Material> materials,
        String model,
        String version,
        List<Double> strengths,
        Double defaultStrength,
        Map<String, String> levelsByStrength
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
