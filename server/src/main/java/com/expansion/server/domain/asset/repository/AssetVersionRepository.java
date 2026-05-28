package com.expansion.server.domain.asset.repository;

import com.expansion.server.domain.asset.entity.AssetVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssetVersionRepository extends JpaRepository<AssetVersion, Long> {

    List<AssetVersion> findByAsset_AssetIdOrderByCreatedAtDesc(Long assetId);

    Optional<AssetVersion> findByAsset_AssetIdAndIsCurrentTrue(Long assetId);

    void deleteByAsset_AssetId(Long assetId);
}
