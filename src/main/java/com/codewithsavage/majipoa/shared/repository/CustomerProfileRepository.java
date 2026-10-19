package com.codewithsavage.majipoa.shared.repository;

import com.codewithsavage.majipoa.shared.model.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Long> {
    Optional<CustomerProfile> findByUserId(Long userId);
    List<CustomerProfile> findByStatus(CustomerProfile.CustomerStatus status);
    List<CustomerProfile> findAllByOrderByTotalSpendDesc();
}
