package com.expansion.server.domain.asset.dto;

import com.expansion.server.domain.asset.entity.AssetLicenseType;

/** 라이선스 선택지 + 권한 표시(업로드 드롭다운/상세). */
public record AssetLicenseTypeResponse(
        Long licenseTypeId,
        String name,
        boolean canCommercial,
        boolean canModify,
        boolean canRedistribute,
        boolean requireAttribution,
        boolean isExclusive,
        String description
) {
    public static AssetLicenseTypeResponse of(AssetLicenseType l) {
        return new AssetLicenseTypeResponse(
                l.getLicenseTypeId(), l.getName(),
                l.isCanCommercial(), l.isCanModify(), l.isCanRedistribute(),
                l.isRequireAttribution(), l.isExclusive(), l.getDescription());
    }
}
