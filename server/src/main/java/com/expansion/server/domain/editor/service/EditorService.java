package com.expansion.server.domain.editor.service;

import com.expansion.server.domain.editor.dto.*;
import com.expansion.server.domain.editor.entity.Frame;
import com.expansion.server.domain.editor.entity.Layer;
import com.expansion.server.domain.editor.entity.Project;
import com.expansion.server.domain.editor.entity.ProjectMember;
import com.expansion.server.domain.editor.repository.FrameRepository;
import com.expansion.server.domain.editor.repository.LayerRepository;
import com.expansion.server.domain.editor.repository.ProjectMemberRepository;
import com.expansion.server.domain.editor.repository.ProjectRepository;
import com.expansion.server.domain.user.entity.User;
import com.expansion.server.domain.user.service.EmailVerificationGuard;
import com.expansion.server.domain.user.repository.UserRepository;
import com.expansion.server.global.exception.CustomException;
import com.expansion.server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EditorService {

    private final ProjectRepository projectRepository;
    private final LayerRepository layerRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final FrameRepository frameRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ProjectResponse createProject(Long userId, ProjectCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        EmailVerificationGuard.assertVerified(user);   // 소프트 게이트 — 미인증 시 프로젝트 생성 불가

        Project project = Project.builder()
                .user(user)
                .title(request.getTitle())
                .width(request.getWidth())
                .height(request.getHeight())
                .backgroundColor(request.getBackgroundColor())
                .thumbnailUrl(request.getThumbnailUrl())
                .isPublic(request.isPublic())
                .status("ACTIVE")
                .aiAnalyzed(false)
                .build();

        projectRepository.save(project);

        Frame defaultFrame = Frame.builder()
                .project(project)
                .frameOrder(0)
                .duration(1000) // 기본 프레임 지속 시간 설정 (예: 1000ms)
                .build();
                
        frameRepository.save(defaultFrame);

        Layer defaultLayer = Layer.builder()
                .frame(defaultFrame)
                .name("Layer 1")
                .layerOrder(0)
                .blendMode("NORMAL")
                .isLocked(false)
                .isVisible(true)
                .opacity(1.0f)
                .build();

        layerRepository.save(defaultLayer);

        List<LayerResponse> layerResponses = List.of(LayerResponse.of(defaultLayer));
        List<FrameResponse> frameResponses = List.of(FrameResponse.of(defaultFrame, layerResponses));
        CanvasResponse canvasResponse = CanvasResponse.of(frameResponses);
        
        return toResponse(project, canvasResponse);
    }

    public ProjectResponse getProject(Long userId, Long projectId) {
        Project project = findActiveProject(projectId);

        boolean isOwner = project.getUser().getUserId().equals(userId);
        boolean isMember = projectMemberRepository.existsByProject_ProjectIdAndUser_UserId(projectId, userId);

        if (!isOwner && !isMember && !project.isPublic()) {
            throw new CustomException(ErrorCode.PROJECT_ACCESS_DENIED);
        }

        List<Frame> frames = frameRepository.findByProject_ProjectIdOrderByFrameOrderAsc(projectId);

        List<FrameResponse> frameResponses = frames.stream()
                .map(f -> {
                    List<LayerResponse> layerResponses = layerRepository
                            .findByFrame_FrameIdOrderByLayerOrderAsc(f.getFrameId())
                            .stream()
                            .map(LayerResponse::of)
                            .collect(Collectors.toList());
                    return FrameResponse.of(f, layerResponses);
                })
                .collect(Collectors.toList());

        CanvasResponse canvasResponse = CanvasResponse.of(frameResponses);
        return toResponse(project, canvasResponse);
    }

    public Page<ProjectSummary> getMyProjects(Long userId, Pageable pageable) {
        return projectRepository.findByUser_UserIdAndStatus(userId, "ACTIVE", pageable)
                .map(ProjectSummary::of);
    }

    @Transactional
    public ProjectResponse updateProject(Long userId, Long projectId, ProjectUpdateRequest request) {
        Project project = getOwnedProject(userId, projectId);
        boolean isPublic = request.getIsPublic() != null ? request.getIsPublic() : project.isPublic();
        project.update(request.getTitle(), request.getWidth(), request.getHeight(), request.getThumbnailUrl(), isPublic);
        
        // 계층 구조로 LayerResponse -> FrameResponse -> CanvasResponse를 거쳐 ProjectResponse를 생성
        List<Frame> frames = frameRepository.findByProject_ProjectIdOrderByFrameOrderAsc(projectId);
        List<FrameResponse> frameResponses = frames.stream()
                .map(f -> {
                    List<LayerResponse> layerResponses = layerRepository
                            .findByFrame_FrameIdOrderByLayerOrderAsc(f.getFrameId())
                            .stream()
                            .map(LayerResponse::of)
                            .collect(Collectors.toList());
                    return FrameResponse.of(f, layerResponses);
                })
                .collect(Collectors.toList());
                
        CanvasResponse canvasResponse = CanvasResponse.of(frameResponses);
        return toResponse(project, canvasResponse);
    }

    @Transactional
    public void deleteProject(Long userId, Long projectId) {
        Project project = getOwnedProject(userId, projectId);
        project.softDelete();
    }

    @Transactional
    public ProjectResponse saveCanvasData(Long userId, Long projectId, CanvasSaveRequest requests) {
        Project project = findActiveProject(projectId);

        boolean isOwner = project.getUser().getUserId().equals(userId);
        boolean isMember = projectMemberRepository.existsByProject_ProjectIdAndUser_UserId(projectId, userId);

        if (!isOwner && !isMember) {
            throw new CustomException(ErrorCode.PROJECT_ACCESS_DENIED);
        }

        // 팔레트: 보낸 경우에만 갱신(null이면 기존 유지)
        if (requests.getPalette() != null) {
            project.updatePaletteData(writePalette(requests.getPalette()));
        }

        // 프로젝트의 기존 프레임 삭제(레이어도 함께 삭제됨)
        frameRepository.deleteByProject_ProjectId(projectId);
        frameRepository.flush();

        List<FrameResponse> frameResponses = new ArrayList<>();
        
        // 프레임 및 레이어 저장 후 ProjectResponse에 CanvasResponse를 포함하여 반환
        for (FrameSaveRequest frameRequest : requests.getFrameSaveRequests()){
                // Frame Save
                Frame newFrame = Frame.builder()
                        .project(project)
                        .frameOrder(frameRequest.getFrameOrder())
                        .duration(frameRequest.getDuration())
                        .build();
                Frame savedFrame = frameRepository.save(newFrame);
                
                // Layer Save
                List<Layer> newLayers = frameRequest.getLayerSaveRequests().stream()
                        .map(layerReq -> Layer.builder()
                                .frame(savedFrame)
                                .name(layerReq.getName())
                                .layerOrder(layerReq.getLayerOrder())
                                .blendMode(layerReq.getBlendMode())
                                .isLocked(layerReq.isLocked())
                                .isVisible(layerReq.isVisible())
                                .opacity(layerReq.getOpacity())
                                .fileUrl(layerReq.getFileUrl())
                                .build())
                        .collect(Collectors.toList());
                List<Layer> savedLayers = layerRepository.saveAll(newLayers);

                List<LayerResponse> layerResponses = savedLayers.stream()
                        .map(LayerResponse::of)
                        .collect(Collectors.toList());

                FrameResponse frameResponse = FrameResponse.of(savedFrame, layerResponses);
                frameResponses.add(frameResponse);
        }

        CanvasResponse canvasResponse = CanvasResponse.of(frameResponses);
        return toResponse(project, canvasResponse);
    }

    @Transactional
    public void addMember(Long ownerId, Long projectId, Long targetUserId) {
        Project project = getOwnedProject(ownerId, projectId);

        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (projectMemberRepository.existsByProject_ProjectIdAndUser_UserId(projectId, targetUserId)) {
            return;
        }

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        ProjectMember member = ProjectMember.builder()
                .project(project)
                .user(targetUser)
                .inviter(owner)
                .permission("VIEW")
                .build();

        projectMemberRepository.save(member);
    }

    @Transactional
    public void removeMember(Long ownerId, Long projectId, Long targetUserId) {
        getOwnedProject(ownerId, projectId);

        ProjectMember member = projectMemberRepository
                .findByProject_ProjectIdAndUser_UserId(projectId, targetUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        projectMemberRepository.delete(member);
    }

    /** 삭제(DELETED)된 프로젝트는 없는 것으로 취급 */
    private Project findActiveProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));
        if (project.isDeleted()) {
            throw new CustomException(ErrorCode.PROJECT_NOT_FOUND);
        }
        return project;
    }

    private ProjectResponse toResponse(Project project, CanvasResponse canvasResponse) {
        return ProjectResponse.of(project, canvasResponse, readPalette(project.getPaletteData()));
    }

    private String writePalette(List<String> colors) {
        List<String> normalized = colors.stream().map(c -> c.toLowerCase(java.util.Locale.ROOT)).distinct().toList();
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (JacksonException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT, e);
        }
    }

    /** 옛 데이터·깨진 값은 팔레트 없음(null)으로 — 프로젝트 열기를 막지 않음 */
    private List<String> readPalette(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JacksonException e) {
            log.warn("프로젝트 팔레트 읽기 실패 — 무시: {}", e.getMessage());
            return null;
        }
    }

    private Project getOwnedProject(Long userId, Long projectId) {
        Project project = findActiveProject(projectId);

        if (!project.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.PROJECT_ACCESS_DENIED);
        }

        return project;
    }
}
