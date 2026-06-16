package com.expansion.server.domain.asset.repository;

import com.expansion.server.domain.asset.entity.AssetDownload;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetDownloadRepository extends JpaRepository<AssetDownload, Long> {

    boolean existsByUser_UserIdAndAsset_AssetId(Long userId, Long assetId);
}
