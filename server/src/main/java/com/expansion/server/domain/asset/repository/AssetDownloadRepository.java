package com.expansion.server.domain.asset.repository;

import com.expansion.server.domain.asset.entity.AssetDownload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetDownloadRepository extends JpaRepository<AssetDownload, Long> {

    boolean existsByUser_UserIdAndAsset_AssetId(Long userId, Long assetId);

    /**
     * 다운로드 기록을 원자적으로 삽입. 이미 (user, asset) 행이 있으면 무시(ON CONFLICT DO NOTHING).
     * 반환값 = 삽입된 행 수(1=새 다운로드, 0=이미 받음) → 1일 때만 카운트 증가하면 race-free.
     */
    @Modifying
    @Query(value = """
            INSERT INTO asset_downloads (user_id, asset_id)
            VALUES (:userId, :assetId)
            ON CONFLICT (user_id, asset_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId, @Param("assetId") Long assetId);
}
