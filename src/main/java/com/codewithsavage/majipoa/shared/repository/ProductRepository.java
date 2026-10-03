package com.codewithsavage.majipoa.shared.repository;

import com.codewithsavage.majipoa.shared.model.Product;
import com.codewithsavage.majipoa.shared.model.Product.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Database access for Product. Scoped to a vendor to prevent cross-vendor data leaks. */
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByVendorId(Long vendorId);
    List<Product> findByVendorIdAndStatus(Long vendorId, ProductStatus status);
}
