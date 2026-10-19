package com.codewithsavage.majipoa.admin.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin Payments page.
 *
 * GET  /api/admin/payments?tab=UNRECONCILED|ALL|FAILED&search=   -> table + tab badges
 * GET  /api/admin/payments/summary                               -> the four KPI cards
 * GET  /api/admin/payments/failures                              -> "Failures by reason" (last 7 days)
 * POST /api/admin/payments/{id}/match   body: { "orderRef": "#WM10301" }
 *
 * /api/admin/** is already restricted to ADMIN in SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final AdminPaymentService service;

    @GetMapping
    public AdminPaymentDtos.PaymentsPageResponse list(
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) String search) {
        return service.getPage(tab, search);
    }

    @GetMapping("/summary")
    public AdminPaymentDtos.SummaryDto summary() {
        return service.getSummary();
    }

    @GetMapping("/failures")
    public List<AdminPaymentDtos.FailureReasonDto> failures() {
        return service.getFailures();
    }

    @PostMapping("/{id}/match")
    public AdminPaymentDtos.PaymentRowDto match(
            @PathVariable Long id,
            @RequestBody AdminPaymentDtos.MatchRequest body) {
        return service.match(id, body.orderRef());
    }
}