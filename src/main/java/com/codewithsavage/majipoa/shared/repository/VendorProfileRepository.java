package com.codewithsavage.majipoa.shared.repository;

import com.codewithsavage.majipoa.shared.model.VendorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Database access for VendorProfile — looked up via the owning user's id. */
public interface VendorProfileRepository extends JpaRepository<VendorProfile, Long> {
    Optional<VendorProfile> findByUserId(Long userId);
}
