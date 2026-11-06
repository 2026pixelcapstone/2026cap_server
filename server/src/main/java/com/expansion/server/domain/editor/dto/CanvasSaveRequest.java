package com.expansion.server.domain.editor.dto;

import java.util.List;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CanvasSaveRequest {
    private List<FrameSaveRequest> frameSaveRequests;

    /** 프로젝트 팔레트(#RRGGBB 또는 #RRGGBBAA). null이면 기존 팔레트 유지, 빈 목록이면 비움 */
    @Size(max = 256)
    private List<@Pattern(regexp = "^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$") String> palette;
}
