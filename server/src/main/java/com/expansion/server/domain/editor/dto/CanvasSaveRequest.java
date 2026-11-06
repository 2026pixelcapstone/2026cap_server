package com.expansion.server.domain.editor.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CanvasSaveRequest {
    private List<FrameSaveRequest> frameSaveRequests;

    /** 프로젝트 팔레트(#RRGGBB 또는 #RRGGBBAA). 목록이 null이면 기존 팔레트 유지, 빈 목록이면 비움. 칸 하나가 null이면 400(@Pattern은 null을 통과시킴) */
    @Size(max = 256)
    private List<@NotNull @Pattern(regexp = "^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$") String> palette;
}
