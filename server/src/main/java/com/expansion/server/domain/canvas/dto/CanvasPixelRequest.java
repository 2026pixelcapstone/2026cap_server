package com.expansion.server.domain.canvas.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CanvasPixelRequest(
        @NotNull @Min(0) @Max(63) Integer x,
        @NotNull @Min(0) @Max(63) Integer y,
        @NotNull @Min(0) @Max(15) Integer color
) {}
