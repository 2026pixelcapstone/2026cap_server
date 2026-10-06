package com.expansion.server.domain.asset.dto;

/** 에셋 삭제 결과 — discontinued=true면 결제 이력이 있어 완전 삭제 대신 판매 중지로 처리됨. */
public record AssetDeleteResponse(boolean discontinued) {
}
