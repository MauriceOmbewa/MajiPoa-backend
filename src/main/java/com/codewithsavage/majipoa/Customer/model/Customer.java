
package com.codewithsavage.majipoa.Customer.model;

import com.codewithsavage.majipoa.shared.model.AppUser;
import com.codewithsavage.majipoa.shared.model.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "Customers_Table")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @Column(nullable = false, unique = true, updatable = false)
    private String customerId;

    @Column(length = 20)
    private String phoneNumber;

    private String email;

    private String firstName;

    private String lastName;

    private String userName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.CUSTOMER;

    /**
     * The authenticated platform user associated with this customer.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    /**
     * Each customer currently has at most one saved address.
     * Removing the address from the customer also removes its row.
     */
    @OneToOne(
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @JoinColumn(name = "address_id")
    private Address address;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @PrePersist
    private void generateCustomerId() {
        if (customerId == null || customerId.isBlank()) {
            customerId = "CUSTOMER-" + UUID.randomUUID();
        }
    }
}
