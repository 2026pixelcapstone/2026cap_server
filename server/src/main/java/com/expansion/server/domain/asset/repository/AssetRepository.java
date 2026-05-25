package com.expansion.server.domain.asset.repository;

import com.expansion.server.domain.asset.entity.Asset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    Page<Asset> findByStatus(String status, Pageable pageable);

    Page<Asset> findByStatusAndIsFree(String status, boolean isFree, Pageable pageable);

    Page<Asset> findByUser_UserId(Long userId, Pageable pageable);

    Page<Asset> findByCategory_CategoryIdAndStatus(Long categoryId, String status, Pageable pageable);

    @Query("SELECT a FROM Asset a JOIN a.assetTags at WHERE at.tag.tagName = :tagName AND a.status = 'ACTIVE' AND (:isFree IS NULL OR a.isFree = :isFree)")
    Page<Asset> findByTagName(@Param("tagName") String tagName, @Param("isFree") Boolean isFree, Pageable pageable);

    @Query("SELECT a FROM Asset a WHERE a.status = 'ACTIVE' AND (a.title LIKE %:keyword% OR a.description LIKE %:keyword%)")
    Page<Asset> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
