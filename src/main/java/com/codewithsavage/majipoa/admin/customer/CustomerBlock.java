package com.codewithsavage.majipoa.admin.customer;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** One row per blocked customer. No row = not blocked. */
@Entity
@Table(name = "Customer_Blocks_Table")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerBlock {

    @Id
    private String customerId;

    @Column(length = 500)
    private String reason;

    private LocalDateTime blockedAt;
    private String blockedBy;
}