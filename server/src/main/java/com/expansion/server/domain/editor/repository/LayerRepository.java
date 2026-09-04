package com.expansion.server.domain.editor.repository;

import com.expansion.server.domain.editor.entity.Layer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface LayerRepository extends JpaRepository<Layer, Long> {

    List<Layer> findByFrame_FrameIdOrderByLayerOrderAsc(Long frameId);

    @Transactional
    void deleteByFrame_FrameId(Long frameId);
}
