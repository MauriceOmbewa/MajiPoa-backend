package com.codewithsavage.majipoa.admin.order;

import com.codewithsavage.majipoa.Customer.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AdminOrderRepository extends JpaRepository<Order, String> {

    @Query("select distinct o from Orders_Table o join fetch o.customer left join fetch o.vendor left join fetch o.items i left join fetch i.product")
    List<Order> findAllWithDetails();

    @Query("select o from Orders_Table o join fetch o.customer left join fetch o.vendor where o.orderId = :id")
    Optional<Order> findDetailed(@Param("id") String id);
}