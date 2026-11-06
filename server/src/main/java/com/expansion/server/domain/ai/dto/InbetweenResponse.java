package com.expansion.server.domain.ai.dto;

import java.util.List;

/**
 * 중간 동작 제안 응답 — 파이썬 서비스(ml/serve /inbetween) 응답 그대로.
 *
 * @param frames    사이 프레임 t = 0.1 … 0.9(9장), 각 PNG base64(A와 같은 캔버스 크기)
 * @param parts     작업물 해석 — A의 부위 덩어리를 덩어리마다 다른 색으로 칠한 PNG base64(겹쳐 보기용)
 * @param joints    작업물 해석 — A의 관절점(칸 좌표)
 * @param partCount 덩어리 수
 * @param model     모델 이름
 * @param elapsedMs 파이썬 계산 시간(ms)
 */
public record InbetweenResponse(
        List<Frame> frames,
        String parts,
        List<Joint> joints,
        Integer partCount,
        String model,
        Double elapsedMs
) {
    public record Frame(Double t, String image) {
    }

    public record Joint(Double x, Double y) {
    }
}
