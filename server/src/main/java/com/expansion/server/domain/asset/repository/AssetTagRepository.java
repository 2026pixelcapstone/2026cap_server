package com.expansion.server.domain.asset.repository;

import com.expansion.server.domain.asset.entity.AssetTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetTagRepository extends JpaRepository<AssetTag, Long> {

    List<AssetTag> findByAsset_AssetId(Long assetId);

    List<AssetTag> findByAsset_AssetIdIn(List<Long> assetIds);

    void deleteByAsset_AssetId(Long assetId);
}
