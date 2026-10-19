package com.codewithsavage.majipoa.admin.order;

import com.codewithsavage.majipoa.shared.model.MpesaPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AdminMpesaPaymentRepository extends JpaRepository<MpesaPayment, Long> {

    List<MpesaPayment> findByMatchedOrderRefIn(Collection<String> orderRefs);
}