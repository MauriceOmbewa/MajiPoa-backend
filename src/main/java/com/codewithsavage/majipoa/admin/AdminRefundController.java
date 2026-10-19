package com.codewithsavage.majipoa.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/refunds")
@RequiredArgsConstructor
public class AdminRefundController {

    private final AdminService adminService;

    @GetMapping
    public AdminDtos.RefundsResponse getRefunds() {
        return adminService.getRefunds();
    }

    @PostMapping("/{id}/approve-refund")
    public AdminDtos.RefundCaseDto approveRefund(
            @PathVariable Long id,
            @RequestBody AdminDtos.ApproveRefundRequest req) {
        return adminService.approveRefund(id, req);
    }

    @PostMapping("/{id}/reject")
    public AdminDtos.RefundCaseDto rejectRefund(
            @PathVariable Long id,
            @RequestBody AdminDtos.CaseActionDto req) {
        return adminService.rejectRefund(id, req);
    }
}
