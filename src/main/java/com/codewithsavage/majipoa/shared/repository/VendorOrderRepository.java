package com.codewithsavage.majipoa.shared.repository;

import com.codewithsavage.majipoa.shared.model.VendorOrder;
import com.codewithsavage.majipoa.shared.model.VendorOrder.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Database access for VendorOrder, with aggregate queries for dashboard stats. */
public interface VendorOrderRepository extends JpaRepository<VendorOrder, Long> {

    List<VendorOrder> findByVendorIdOrderByPlacedAtDesc(Long vendorId);

    List<VendorOrder> findByVendorIdAndStatusOrderByPlacedAtDesc(Long vendorId, OrderStatus status);

    Optional<VendorOrder> findByOrderRef(String orderRef);

    @Query("SELECT COUNT(o) FROM VendorOrder o WHERE o.vendor.id = :vendorId AND o.placedAt >= :since")
    long countByVendorIdSince(@Param("vendorId") Long vendorId, @Param("since") Instant since);

    @Query("""
        SELECT COALESCE(SUM(l.qty * l.unitPrice), 0)
        FROM VendorOrder o JOIN o.lines l
        WHERE o.vendor.id = :vendorId
          AND o.status = 'COMPLETED'
          AND o.deliveredAt >= :since
    """)
    long sumSalesToday(@Param("vendorId") Long vendorId, @Param("since") Instant since);
}
