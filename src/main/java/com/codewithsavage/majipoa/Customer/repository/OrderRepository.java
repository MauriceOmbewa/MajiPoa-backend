package com.codewithsavage.majipoa.Customer.repository;

import com.codewithsavage.majipoa.Customer.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
}
