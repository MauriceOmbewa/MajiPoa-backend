package com.codewithsavage.majipoa.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/verification")
@RequiredArgsConstructor
public class AdminVerificationController {

    private final AdminService adminService;

    @GetMapping("/queue")
    public List<AdminDtos.VerificationQueueItem> getQueue() {
        return adminService.getVerificationQueue();
    }

    @GetMapping("/{vendorId}")
    public AdminDtos.VerificationDetail getDetail(@PathVariable Long vendorId) {
        return adminService.getVerificationDetail(vendorId);
    }

    @PostMapping("/{vendorId}/approve")
    public AdminDtos.VerificationDetail approve(@PathVariable Long vendorId) {
        return adminService.approveVerification(vendorId);
    }

    @PostMapping("/{vendorId}/reject")
    public AdminDtos.VerificationDetail reject(
            @PathVariable Long vendorId,
            @RequestBody AdminDtos.VerificationNoteRequest req) {
        return adminService.rejectVerification(vendorId, req);
    }

    @PostMapping("/{vendorId}/request-changes")
    public AdminDtos.VerificationDetail requestChanges(
            @PathVariable Long vendorId,
            @RequestBody AdminDtos.VerificationNoteRequest req) {
        return adminService.requestVerificationChanges(vendorId, req);
    }

    @PostMapping("/{vendorId}/documents/{docId}/approve")
    public AdminDtos.DocDto approveDocument(
            @PathVariable Long vendorId,
            @PathVariable Long docId) {
        return adminService.approveDocument(vendorId, docId);
    }

    @PostMapping("/{vendorId}/documents/{docId}/query")
    public AdminDtos.DocDto queryDocument(
            @PathVariable Long vendorId,
            @PathVariable Long docId,
            @RequestBody AdminDtos.DocDecisionRequest req) {
        return adminService.queryDocument(vendorId, docId, req);
    }

    @PostMapping("/{vendorId}/documents/request")
    public AdminDtos.DocDto requestDocument(
            @PathVariable Long vendorId,
            @RequestBody AdminDtos.DocDecisionRequest req) {
        return adminService.requestDocument(vendorId, req);
    }
}
