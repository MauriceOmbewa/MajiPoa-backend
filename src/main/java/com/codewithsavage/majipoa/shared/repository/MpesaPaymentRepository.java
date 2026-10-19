package com.codewithsavage.majipoa.shared.repository;

import com.codewithsavage.majipoa.shared.model.MpesaPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MpesaPaymentRepository extends JpaRepository<MpesaPayment, Long> {
    List<MpesaPayment> findByStatus(MpesaPayment.PaymentStatus status);
    Optional<MpesaPayment> findByMpesaCode(String code);
    List<MpesaPayment> findAllByOrderByReceivedAtDesc();
}
