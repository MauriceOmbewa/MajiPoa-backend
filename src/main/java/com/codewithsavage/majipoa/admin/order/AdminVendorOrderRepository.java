package com.codewithsavage.majipoa.admin.order;

import com.codewithsavage.majipoa.shared.model.VendorOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Admin-side lookups on the vendor's copy of an order. A VendorOrder reuses the customer
 * order's id as its orderRef, so the two records can always be matched.
 */
public interface AdminVendorOrderRepository extends JpaRepository<VendorOrder, Long> {

    Optional<VendorOrder> findByOrderRef(String orderRef);

    List<VendorOrder> findByOrderRefIn(Collection<String> orderRefs);
}