package com.expansion.server.domain.asset.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "asset_license_types")
@Getter
@NoArgsConstructor
public class AssetLicenseType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "license_type_id")
    private Long licenseTypeId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "can_commercial", nullable = false)
    private boolean canCommercial;

    @Column(name = "can_modify", nullable = false)
    private boolean canModify;

    @Column(name = "can_redistribute", nullable = false)
    private boolean canRedistribute;

    @Column(name = "require_attribution", nullable = false)
    private boolean requireAttribution;

    @Column(name = "is_exclusive", nullable = false)
    private boolean isExclusive;

    @Column(columnDefinition = "TEXT")
    private String description;
}
