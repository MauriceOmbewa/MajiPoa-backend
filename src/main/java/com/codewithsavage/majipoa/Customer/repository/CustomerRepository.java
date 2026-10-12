package com.codewithsavage.majipoa.Customer.repository;

import com.codewithsavage.majipoa.Customer.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, String> {

    Optional<Customer> findByUserId(Long userId);
}