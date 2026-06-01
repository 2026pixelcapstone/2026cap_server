package com.expansion.server.domain.commission.entity;

import com.expansion.server.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "commission_services")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ArtistService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "service_id")
    private Long serviceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id", nullable = false)
    private User artist;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "service_type", nullable = false, length = 20)
    private String serviceType;  // OPTION / QUOTE

    @Column(name = "base_price", precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "price_min", precision = 10, scale = 2)
    private BigDecimal priceMin;

    @Column(name = "price_max", precision = 10, scale = 2)
    private BigDecimal priceMax;

    @Column(name = "estimated_days")
    private Integer estimatedDays;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String category = "기타";

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "OPEN";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void close() {
        this.status = "CLOSED";
    }

    public void update(String title, String description, String serviceType,
                       BigDecimal basePrice, BigDecimal priceMin, BigDecimal priceMax,
                       Integer estimatedDays, String category) {
        if (title != null) this.title = title;
        if (description != null) this.description = description;
        if (serviceType != null) this.serviceType = serviceType;
        if (basePrice != null) this.basePrice = basePrice;
        if (priceMin != null) this.priceMin = priceMin;
        if (priceMax != null) this.priceMax = priceMax;
        if (estimatedDays != null) this.estimatedDays = estimatedDays;
        if (category != null) this.category = category;
    }
}
