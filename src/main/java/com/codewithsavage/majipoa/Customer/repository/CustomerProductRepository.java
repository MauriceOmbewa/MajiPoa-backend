package com.codewithsavage.majipoa.Customer.repository;

import com.codewithsavage.majipoa.Customer.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CustomerProductRepository extends JpaRepository<Product, String> {
    List<Product> findByActiveTrue();

    @Query("SELECT p FROM CustomerCatalogProduct p WHERE p.active = true AND p.stockQuantity > 0 AND LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Product> searchProducts(@Param("keyword") String keyword);
}
