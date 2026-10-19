package com.codewithsavage.majipoa.rider;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RiderRepository extends JpaRepository<Rider, Long> {

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPlateNumber(String plateNumber);

    List<Rider> findByStatus(RiderStatus status);

    /** The rider profile that belongs to a login account (AppUser). */
    Optional<Rider> findByUserId(Long userId);

    /** Used once, to link a rider an admin created earlier to the account with the same email. */
    Optional<Rider> findByEmailIgnoreCase(String email);
}
