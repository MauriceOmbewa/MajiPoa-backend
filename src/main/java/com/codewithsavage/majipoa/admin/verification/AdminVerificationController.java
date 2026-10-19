package com.codewithsavage.majipoa.admin.verification;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Admin Verification page.
 *
 * GET  /api/admin/vendor-verification/queue
 * GET  /api/admin/vendor-verification/{vendorId}
 *
 * POST /api/admin/vendor-verification/{vendorId}/documents/{docId}/approve
 * POST /api/admin/vendor-verification/{vendorId}/documents/{docId}/query     body: { "note": "..." }
 * POST /api/admin/vendor-verification/{vendorId}/documents/{docId}/request   body: { "note": "..." } (optional)
 *
 * POST /api/admin/vendor-verification/{vendorId}/approve          body: { "note": "..." } (optional)
 * POST /api/admin/vendor-verification/{vendorId}/request-changes  body: { "note": "..." }
 * POST /api/admin/vendor-verification/{vendorId}/reject           body: { "note": "..." }
 *
 * Every action returns the refreshed vendor detail, so the page can re-render from the response.
 * The base path differs from /api/admin/verification on purpose, so it does not clash with the
 * routes in your existing admin controller. /api/admin/** is already restricted to ADMIN.
 */
@RestController
@RequestMapping("/api/admin/vendor-verification")
@RequiredArgsConstructor
public class AdminVerificationController {

    private final AdminVerificationService service;

    @GetMapping("/queue")
    public AdminVerificationDtos.QueueResponse queue() {
        return service.getQueue();
    }

    @GetMapping("/{vendorId}")
    public AdminVerificationDtos.DetailDto detail(@PathVariable Long vendorId) {
        return service.getDetail(vendorId);
    }

    // ── Documents ──

    @PostMapping("/{vendorId}/documents/{docId}/approve")
    public AdminVerificationDtos.DetailDto approveDocument(
            @PathVariable Long vendorId, @PathVariable Long docId, Authentication auth) {
        return service.approveDocument(vendorId, docId, auth.getName());
    }

    @PostMapping("/{vendorId}/documents/{docId}/query")
    public AdminVerificationDtos.DetailDto queryDocument(
            @PathVariable Long vendorId, @PathVariable Long docId,
            @RequestBody AdminVerificationDtos.DecisionRequest body, Authentication auth) {
        return service.queryDocument(vendorId, docId, body.note(), auth.getName());
    }

    @PostMapping("/{vendorId}/documents/{docId}/request")
    public AdminVerificationDtos.DetailDto requestDocument(
            @PathVariable Long vendorId, @PathVariable Long docId,
            @RequestBody(required = false) AdminVerificationDtos.DecisionRequest body, Authentication auth) {
        return service.requestDocument(vendorId, docId, body == null ? null : body.note(), auth.getName());
    }

    // ── Vendor decisions ──

    @PostMapping("/{vendorId}/approve")
    public AdminVerificationDtos.DetailDto approve(
            @PathVariable Long vendorId,
            @RequestBody(required = false) AdminVerificationDtos.DecisionRequest body, Authentication auth) {
        return service.approveVendor(vendorId, body == null ? null : body.note(), auth.getName());
    }

    @PostMapping("/{vendorId}/request-changes")
    public AdminVerificationDtos.DetailDto requestChanges(
            @PathVariable Long vendorId,
            @RequestBody AdminVerificationDtos.DecisionRequest body, Authentication auth) {
        return service.requestChanges(vendorId, body.note(), auth.getName());
    }

    @PostMapping("/{vendorId}/reject")
    public AdminVerificationDtos.DetailDto reject(
            @PathVariable Long vendorId,
            @RequestBody AdminVerificationDtos.DecisionRequest body, Authentication auth) {
        return service.rejectVendor(vendorId, body.note(), auth.getName());
    }
}