package com.codewithsavage.majipoa.admin.order;

import com.codewithsavage.majipoa.shared.model.VendorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminVendorRepository extends JpaRepository<VendorProfile, Long> {
}