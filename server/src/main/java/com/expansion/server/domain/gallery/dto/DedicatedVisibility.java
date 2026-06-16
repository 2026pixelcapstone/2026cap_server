package com.expansion.server.domain.gallery.dto;

/**
 * 전용 갤러리 공개 항목 토글 — dedicated_visibility(JSONB)에 저장.
 * 작품 뷰어는 항상 공개라 토글 없음. download 기본 비공개(스펙 §6).
 * download=false면 서버 응답에서 file_url 을 null 마스킹한다.
 */
public record DedicatedVisibility(
        Boolean canvas,    // 캔버스 크기/배경 공개
        Boolean palette,   // 팔레트 공개
        Boolean layers,    // 레이어 구조 공개
        Boolean download   // .ppit 원본 다운로드 제공 (기본 false)
) {}
