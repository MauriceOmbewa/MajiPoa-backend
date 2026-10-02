package com.codewithsavage.majipoa.repository;

import com.codewithsavage.majipoa.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByGoogleSub(String googleSub);
}