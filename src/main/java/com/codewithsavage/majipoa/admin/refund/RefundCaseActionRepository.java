package com.codewithsavage.majipoa.admin.refund;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RefundCaseActionRepository extends JpaRepository<RefundCaseAction, Long> {

    List<RefundCaseAction> findByRefundCaseIdInOrderByCreatedAtAsc(Collection<Long> refundCaseIds);
}