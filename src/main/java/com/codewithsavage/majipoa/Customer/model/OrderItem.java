package com.codewithsavage.majipoa.Customer.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity(name = "OrderItem_Table")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class OrderItem {
    @Id
    @Column(nullable = false, unique = true, updatable = false)
    private String orderItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    private Integer quantity;
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @PrePersist
    private void generateCartItemId() {
        if (orderItemId == null || orderItemId.isBlank()) {
            orderItemId = "ORDERITEM-" + UUID.randomUUID();
        }
    }
}
