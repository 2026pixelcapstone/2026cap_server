package com.expansion.server.domain.editor.service;

import com.expansion.server.domain.editor.dto.*;
import com.expansion.server.domain.editor.entity.Layer;
import com.expansion.server.domain.editor.entity.Project;
import com.expansion.server.domain.editor.entity.ProjectMember;
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

        Layer defaultLayer = Layer.builder()
                .project(project)
                .name("Layer 1")
                .layerOrder(0)
                .blendMode("NORMAL")
                .isLocked(false)
                .isVisible(true)
                .opacity(1.0f)
                .build();

        layerRepository.save(defaultLayer);

        List<LayerResponse> layerResponses = List.of(LayerResponse.of(defaultLayer));
        return ProjectResponse.of(project, layerResponses);
    }

    public ProjectResponse getProject(Long userId, Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        boolean isOwner = project.getUser().getUserId().equals(userId);
        boolean isMember = projectMemberRepository.existsByProject_ProjectIdAndUser_UserId(projectId, userId);

        if (!isOwner && !isMember && !project.isPublic()) {
            throw new CustomException(ErrorCode.PROJECT_ACCESS_DENIED);
        }

        List<LayerResponse> layers = layerRepository
                .findByProject_ProjectIdOrderByLayerOrderAsc(projectId)
                .stream()
                .map(LayerResponse::of)
                .collect(Collectors.toList());

        return ProjectResponse.of(project, layers);
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

        List<LayerResponse> layers = layerRepository
                .findByProject_ProjectIdOrderByLayerOrderAsc(projectId)
                .stream()
                .map(LayerResponse::of)
                .collect(Collectors.toList());

        return ProjectResponse.of(project, layers);
    }

    @Transactional
    public void deleteProject(Long userId, Long projectId) {
        Project project = getOwnedProject(userId, projectId);
        project.softDelete();
    }

    @Transactional
    public ProjectResponse saveLayers(Long userId, Long projectId, List<LayerSaveRequest> requests) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new CustomException(ErrorCode.PROJECT_NOT_FOUND));

        boolean isOwner = project.getUser().getUserId().equals(userId);
        boolean isMember = projectMemberRepository.existsByProject_ProjectIdAndUser_UserId(projectId, userId);

        if (!isOwner && !isMember) {
            throw new CustomException(ErrorCode.PROJECT_ACCESS_DENIED);
        }

        layerRepository.deleteByProject_ProjectId(projectId);
        layerRepository.flush();

        List<Layer> newLayers = requests.stream()
                .map(req -> Layer.builder()
                        .project(project)
                        .name(req.getName())
                        .layerOrder(req.getLayerOrder())
                        .blendMode(req.getBlendMode())
                        .isLocked(req.isLocked())
                        .isVisible(req.isVisible())
                        .opacity(req.getOpacity())
                        .fileUrl(req.getFileUrl())
                        .pixelData(req.getPixelData())
                        .build())
                .collect(Collectors.toList());

        layerRepository.saveAll(newLayers);

        List<LayerResponse> layerResponses = newLayers.stream()
                .map(LayerResponse::of)
                .collect(Collectors.toList());

        return ProjectResponse.of(project, layerResponses);
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
