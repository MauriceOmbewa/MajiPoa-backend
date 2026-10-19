package com.codewithsavage.majipoa.admin.refund;

import com.codewithsavage.majipoa.shared.model.VendorOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

/** Used only to work out the refund rate (cases this month / orders this month). */
public interface RefundOrderStatsRepository extends JpaRepository<VendorOrder, Long> {

    long countByPlacedAtGreaterThanEqual(Instant from);
}