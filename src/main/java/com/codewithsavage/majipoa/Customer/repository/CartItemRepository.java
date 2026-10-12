package com.codewithsavage.majipoa.Customer.repository;

import com.codewithsavage.majipoa.Customer.model.CartItem;
import com.codewithsavage.majipoa.Customer.model.Customer;
import com.codewithsavage.majipoa.Customer.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, String> {
    CartItem findByCustomerAndProduct(Customer customer, Product product);

    void deleteByCustomerAndProduct(Customer customer, Product product);

    List<CartItem> findByCustomer (Customer customer);

    void deleteByCustomer(Customer customer);
}
