package com.expansion.server.domain.editor.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CanvasResponse {

    private List<FrameResponse> frameResponses;

    public static CanvasResponse of(List<FrameResponse> frameResponses) {
        return CanvasResponse.builder()
                .frameResponses(frameResponses)
                .build();
    }
}
