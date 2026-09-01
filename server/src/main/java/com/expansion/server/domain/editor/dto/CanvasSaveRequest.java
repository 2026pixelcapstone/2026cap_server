package com.expansion.server.domain.editor.dto;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CanvasSaveRequest {
    
    private List<FrameSaveRequest> frameSaveRequests;
}
