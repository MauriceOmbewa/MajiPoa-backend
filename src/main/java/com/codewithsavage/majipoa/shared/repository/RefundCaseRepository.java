package com.codewithsavage.majipoa.shared.repository;

import com.codewithsavage.majipoa.shared.model.RefundCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefundCaseRepository extends JpaRepository<RefundCase, Long> {
    List<RefundCase> findByStatus(RefundCase.RefundStatus status);
    List<RefundCase> findAllByOrderByOpenedAtDesc();
}
