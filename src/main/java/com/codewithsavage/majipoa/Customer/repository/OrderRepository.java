package com.codewithsavage.majipoa.Customer.repository;

import com.codewithsavage.majipoa.Customer.model.Order;
import com.codewithsavage.majipoa.Customer.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    // A customer's order history, newest first
    List<Order> findByCustomer_CustomerIdOrderByCreatedAtDesc(
            String customerId
    );

    // Ensure a customer can only retrieve their own order
    Optional<Order> findByOrderIdAndCustomer_CustomerId(
            String orderId,
            String customerId
    );

    // Orders whose status is not in the excluded statuses
    List<Order> findByStatusNotInOrderByCreatedAtDesc(
            List<OrderStatus> statuses
    );
}
