package com.expansion.server.domain.editor.dto;

import java.util.List;

import com.expansion.server.domain.editor.entity.Frame;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FrameResponse {
    private Long frameId;
    private int frameOrder;
    private int duration;
    private List<LayerResponse> layerResponses;

    public static FrameResponse of(Frame frame, List<LayerResponse> layerResponses) {
        return FrameResponse.builder()
                .frameId(frame.getFrameId())
                .frameOrder(frame.getFrameOrder())
                .duration(frame.getDuration())
                .layerResponses(layerResponses)
                .build();
    }
}
