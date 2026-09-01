package com.expansion.server.domain.editor.dto;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FrameSaveRequest {
    private Long frameId;
    private int frameOrder;
    private int duration;
    private List<LayerSaveRequest> layerSaveRequests;
}
