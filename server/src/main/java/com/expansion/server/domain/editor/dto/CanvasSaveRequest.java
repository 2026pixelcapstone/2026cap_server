package com.expansion.server.domain.editor.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CanvasSaveRequest {
    
    private List<FrameSaveRequest> frameSaveRequests;
}
