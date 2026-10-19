package com.codewithsavage.majipoa.admin.payment;

import com.codewithsavage.majipoa.shared.model.RefundCase;
import com.codewithsavage.majipoa.shared.model.RefundCase.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Read-only lookup of refunds for the Payments page.
 * (Kept separate so this package doesn't depend on the refunds page's own repository.)
 */
public interface PaymentRefundRepository extends JpaRepository<RefundCase, Long> {

    List<RefundCase> findByStatusOrderByResolvedAtDesc(RefundStatus status);
}