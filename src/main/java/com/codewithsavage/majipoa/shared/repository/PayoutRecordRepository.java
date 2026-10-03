package com.codewithsavage.majipoa.shared.repository;

import com.codewithsavage.majipoa.shared.model.PayoutRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PayoutRecordRepository extends JpaRepository<PayoutRecord, Long> {

    List<PayoutRecord> findByVendorIdOrderByPaidOnDesc(Long vendorId);

    @Query("SELECT COALESCE(SUM(p.paid), 0) FROM PayoutRecord p WHERE p.vendor.id = :vendorId AND p.paidOn >= :since")
    long sumPaidSince(@Param("vendorId") Long vendorId, @Param("since") LocalDate since);
}
