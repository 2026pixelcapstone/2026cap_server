package com.expansion.server.domain.editor.controller;

import com.expansion.server.domain.editor.dto.*;
import com.expansion.server.domain.editor.service.EditorService;
import com.expansion.server.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/editor")
@RequiredArgsConstructor
public class EditorController {

    private final EditorService editorService;

    @GetMapping("/projects")
    public ApiResponse<Page<ProjectSummary>> getMyProjects(
            @AuthenticationPrincipal Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(editorService.getMyProjects(userId, pageable));
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProjectResponse> createProject(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody ProjectCreateRequest request) {
        return ApiResponse.ok(editorService.createProject(userId, request));
    }

    @GetMapping("/projects/{projectId}")
    public ApiResponse<ProjectResponse> getProject(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId) {
        return ApiResponse.ok(editorService.getProject(userId, projectId));
    }

    @PatchMapping("/projects/{projectId}")
    public ApiResponse<ProjectResponse> updateProject(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId,
            @Valid @RequestBody ProjectUpdateRequest request) {
        return ApiResponse.ok(editorService.updateProject(userId, projectId, request));
    }

    @DeleteMapping("/projects/{projectId}")
    public ApiResponse<Void> deleteProject(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId) {
        editorService.deleteProject(userId, projectId);
        return ApiResponse.ok("프로젝트가 삭제되었습니다.");
    }

    @PostMapping("/projects/{projectId}/canvasData")
    public ApiResponse<ProjectResponse> saveCanvasData(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId,
            @Valid @RequestBody CanvasSaveRequest requests) {
        return ApiResponse.ok(editorService.saveCanvasData(userId, projectId, requests));
    }

    @PostMapping("/projects/{projectId}/members/{targetUserId}")
    public ApiResponse<Void> addMember(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId,
            @PathVariable Long targetUserId) {
        editorService.addMember(userId, projectId, targetUserId);
        return ApiResponse.ok("멤버가 추가되었습니다.");
    }

    @DeleteMapping("/projects/{projectId}/members/{targetUserId}")
    public ApiResponse<Void> removeMember(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long projectId,
            @PathVariable Long targetUserId) {
        editorService.removeMember(userId, projectId, targetUserId);
        return ApiResponse.ok("멤버가 제거되었습니다.");
    }
}
