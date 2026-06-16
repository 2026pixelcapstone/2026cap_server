package com.expansion.server.domain.asset.repository;

import com.expansion.server.domain.asset.entity.AssetLicenseType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetLicenseTypeRepository extends JpaRepository<AssetLicenseType, Long> {

    List<AssetLicenseType> findAllByOrderByLicenseTypeIdAsc();
}
