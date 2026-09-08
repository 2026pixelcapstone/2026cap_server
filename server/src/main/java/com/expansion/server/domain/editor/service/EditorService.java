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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EditorService {

    private final ProjectRepository projectRepository;
    private final LayerRepository layerRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final FrameRepository frameRepository;

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
        
        return ProjectResponse.of(project, canvasResponse);
    }

    public ProjectResponse getProject(Long userId, Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

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
        return ProjectResponse.of(project, canvasResponse);
    }

    public Page<ProjectSummary> getMyProjects(Long userId, Pageable pageable) {
        return projectRepository.findByUser_UserIdAndStatus(userId, "ACTIVE", pageable)
                .map(ProjectSummary::of);
    }

    @Transactional
    public ProjectResponse updateProject(Long userId, Long projectId, ProjectUpdateRequest request) {
        Project project = getOwnedProject(userId, projectId);
        boolean isPublic = request.getIsPublic() != null ? request.getIsPublic() : project.isPublic();
        project.update(request.getTitle(), request.getThumbnailUrl(), isPublic);
        
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
        return ProjectResponse.of(project, canvasResponse);
    }

    @Transactional
    public void deleteProject(Long userId, Long projectId) {
        Project project = getOwnedProject(userId, projectId);
        project.softDelete();
    }

    @Transactional
    public ProjectResponse saveCanvasData(Long userId, Long projectId, CanvasSaveRequest requests) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        boolean isOwner = project.getUser().getUserId().equals(userId);
        boolean isMember = projectMemberRepository.existsByProject_ProjectIdAndUser_UserId(projectId, userId);

        if (!isOwner && !isMember) {
            throw new CustomException(ErrorCode.PROJECT_ACCESS_DENIED);
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
        return ProjectResponse.of(project, canvasResponse);
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

    private Project getOwnedProject(Long userId, Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        if (!project.getUser().getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.PROJECT_ACCESS_DENIED);
        }

        return project;
    }
}
