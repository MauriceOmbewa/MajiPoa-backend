package com.codewithsavage.majipoa.Customer.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "CartItem_Table")
@Data
@NoArgsConstructor
public class CartItem {
    @Id
    @Column(nullable = false, unique = true, updatable = false)
    private String cartItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="customerId", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="productId", nullable = false)
    private Product product;
    private Integer quantity;
    private BigDecimal price;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @PrePersist
    private void generateCartItemId() {
        if (cartItemId == null || cartItemId.isBlank()) {
            cartItemId = "CARTITEM-" + UUID.randomUUID();
        }
    }


}
