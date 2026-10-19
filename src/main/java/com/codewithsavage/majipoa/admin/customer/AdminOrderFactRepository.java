package com.codewithsavage.majipoa.admin.customer;

import com.codewithsavage.majipoa.Customer.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AdminOrderFactRepository extends JpaRepository<Order, String> {

    @Query("""
           select o.customer.customerId as customerId,
                  o.totalAmount as amount,
                  o.createdAt as placedAt,
                  o.status as status
           from Orders_Table o
           """)
    List<OrderFactView> findAllFacts();
}