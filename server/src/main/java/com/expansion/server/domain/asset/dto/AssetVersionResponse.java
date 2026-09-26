package com.expansion.server.domain.asset.dto;

import com.expansion.server.domain.asset.entity.AssetVersion;

import java.time.LocalDateTime;

/**
 * 에셋 다운로드 파일 버전 응답 (버전 히스토리·관리 UI용).
 * fileUrl은 다운로드 권한(무료/구매) 마스킹 대상이라 목록에는 노출하지 않는다.
 */
public record AssetVersionResponse(
        Long versionId,
        int versionNumber,
        String versionName,
        long fileSize,
        String changeNote,
        boolean isCurrent,
        LocalDateTime createdAt
) {
    public static AssetVersionResponse of(AssetVersion v) {
        return new AssetVersionResponse(
                v.getVersionId(),
                v.getVersionNumber(),
                v.getVersionName(),
                v.getFileSize(),
                v.getChangeNote(),
                v.isCurrent(),
                v.getCreatedAt()
        );
    }
}
