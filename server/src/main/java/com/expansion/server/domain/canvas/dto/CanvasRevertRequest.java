package com.expansion.server.domain.canvas.dto;

import jakarta.validation.constraints.NotNull;

public record CanvasRevertRequest(@NotNull Long userId) {}
