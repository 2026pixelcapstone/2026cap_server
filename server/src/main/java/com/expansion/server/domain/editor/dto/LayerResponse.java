package com.expansion.server.domain.editor.dto;

import com.expansion.server.domain.editor.entity.Layer;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class LayerResponse {

    private Long layerId;
    private String name;
    private int layerOrder;
    private String blendMode;
    private boolean isLocked;
    private boolean isVisible;
    private float opacity;
    private String fileUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static LayerResponse of(Layer layer) {
        return LayerResponse.builder()
                .layerId(layer.getLayerId())
                .name(layer.getName())
                .layerOrder(layer.getLayerOrder())
                .blendMode(layer.getBlendMode())
                .isLocked(layer.isLocked())
                .isVisible(layer.isVisible())
                .opacity(layer.getOpacity())
                .fileUrl(layer.getFileUrl())
                .createdAt(layer.getCreatedAt())
                .updatedAt(layer.getUpdatedAt())
                .build();
    }
}
