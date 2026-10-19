package com.codewithsavage.majipoa.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final AdminService adminService;

    @GetMapping
    public AdminDtos.PaymentsResponse getPayments(
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) String search) {
        return adminService.getPayments(tab, search);
    }

    @PostMapping("/reconcile")
    public void reconcile() {
        adminService.reconcilePayments();
    }
}
