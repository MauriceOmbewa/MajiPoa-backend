package com.codewithsavage.majipoa.shared.repository;

import com.codewithsavage.majipoa.shared.model.VendorDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VendorDocumentRepository extends JpaRepository<VendorDocument, Long> {
    List<VendorDocument> findByVendorId(Long vendorId);
}
