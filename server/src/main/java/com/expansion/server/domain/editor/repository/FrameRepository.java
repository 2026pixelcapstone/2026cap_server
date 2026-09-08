package com.expansion.server.domain.editor.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.expansion.server.domain.editor.entity.Frame;

public interface FrameRepository extends JpaRepository<Frame, Long> {
    List<Frame> findByProject_ProjectIdOrderByFrameOrderAsc(Long projectId);

    @Transactional
    void deleteByProject_ProjectId(Long projectId);

}
