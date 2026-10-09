package com.expansion.server.domain.challenge.dto;

/** 참가 결과 — result = ENTERED(새 참가) | REPLACED(기존 참가작 교체) */
public record ChallengeEntryResponse(String result, Long postId) {}
