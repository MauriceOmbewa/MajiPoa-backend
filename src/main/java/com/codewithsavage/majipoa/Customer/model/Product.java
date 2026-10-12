package com.codewithsavage.majipoa.Customer.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity(name = "CustomerCatalogProduct")
@Table(name = "Products_Table")
@Data
@NoArgsConstructor
public class Product {

    @Id
    @Column(nullable = false, unique = true, updatable = false)
    private String productId;

    private String name;
    private String description;
    private BigDecimal price;
    private Integer stockQuantity;
    private String category;
    private String imageUrl;
    private String size;
    private String status;
    private Boolean active = true;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @PrePersist
    private void generateProductId() {
        if (productId == null || productId.isBlank()) {
            productId = "PRODUCT-" + UUID.randomUUID();
        }
    }
}
