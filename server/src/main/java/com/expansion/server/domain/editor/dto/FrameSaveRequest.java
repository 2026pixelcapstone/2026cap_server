package com.expansion.server.domain.editor.dto;

import java.util.List;

import com.expansion.server.domain.editor.entity.Project;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FrameSaveRequest {
    private int frameOrder;
    private int duration;
    private List<LayerSaveRequest> layerSaveRequests;

    public static FrameSaveRequest fromEntity(Project project, int frameOrder, List<LayerSaveRequest> layerSaveRequests) {
        return FrameSaveRequest.builder()
                .frameOrder(frameOrder)
                .layerSaveRequests(layerSaveRequests)
                .build();
    }
}
