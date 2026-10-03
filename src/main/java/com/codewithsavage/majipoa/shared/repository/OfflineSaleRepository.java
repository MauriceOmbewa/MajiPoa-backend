package com.codewithsavage.majipoa.shared.repository;

import com.codewithsavage.majipoa.shared.model.OfflineSale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OfflineSaleRepository extends JpaRepository<OfflineSale, Long> {
    List<OfflineSale> findByVendorIdOrderBySaleDateDesc(Long vendorId);
}
