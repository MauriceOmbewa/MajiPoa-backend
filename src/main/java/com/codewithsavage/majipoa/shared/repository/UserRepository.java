package com.codewithsavage.majipoa.shared.repository;

import com.codewithsavage.majipoa.shared.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Database access for AppUser.
 *
 * We look users up by googleSub (Google's stable "sub" claim) rather than by
 * email, because emails can change but the sub never does.
 */
public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByGoogleSub(String googleSub);
}
