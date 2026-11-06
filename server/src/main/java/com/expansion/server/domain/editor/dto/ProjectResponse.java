package com.expansion.server.domain.editor.dto;

import com.expansion.server.domain.editor.entity.Project;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ProjectResponse {

    private Long projectId;
    private String title;
    private int width;
    private int height;
    private String backgroundColor;
    private String fileUrl;
    private String thumbnailUrl;
    private boolean isPublic;
    private String status;
    private boolean aiAnalyzed;
    private CanvasResponse canvasResponse;
    /** 프로젝트 팔레트(없으면 null — 프론트는 기본 팔레트 유지) */
    private List<String> palette;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProjectResponse of(Project project, CanvasResponse canvasResponse) {
        return of(project, canvasResponse, null);
    }

    public static ProjectResponse of(Project project, CanvasResponse canvasResponse, List<String> palette) {
        return ProjectResponse.builder()
                .projectId(project.getProjectId())
                .title(project.getTitle())
                .width(project.getWidth())
                .height(project.getHeight())
                .backgroundColor(project.getBackgroundColor())
                .fileUrl(project.getFileUrl())
                .thumbnailUrl(project.getThumbnailUrl())
                .isPublic(project.isPublic())
                .status(project.getStatus())
                .aiAnalyzed(project.isAiAnalyzed())
                .canvasResponse(canvasResponse)
                .palette(palette)
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
