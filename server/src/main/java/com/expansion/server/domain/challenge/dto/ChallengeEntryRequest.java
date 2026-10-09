package com.expansion.server.domain.challenge.dto;

import jakarta.validation.constraints.NotNull;

public record ChallengeEntryRequest(@NotNull Long postId) {}
