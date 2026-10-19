package com.codewithsavage.majipoa.Customer.model;

import com.codewithsavage.majipoa.shared.model.VendorProfile;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity(name =  "Orders_Table")
@Data
@NoArgsConstructor
public class Order {
    @Id
    @Column(nullable = false, unique = true, updatable = false)
    private String orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name =  "user_id", nullable = false)
    private Customer customer;

    /** Set by the admin when the order is dispatched. Null means "awaiting dispatch". */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private VendorProfile vendor;

    private LocalDateTime dispatchedAt;

    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PENDING;

    @OneToMany(mappedBy =  "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @PrePersist
    private void generateCartItemId() {
        if (orderId == null || orderId.isBlank()) {
            orderId = "ORDER-" + UUID.randomUUID();
        }
    }

}