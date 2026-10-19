package com.codewithsavage.majipoa.admin.vendor;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Read endpoints for the admin Vendors page.
 * Approve / suspend / reinstate stay in AdminService (and its controller).
 *
 * GET /api/admin/vendors          -> table rows + tab counts
 * GET /api/admin/vendors/export   -> CSV download of the same (filtered) rows
 *
 * Query params (all optional):
 *   tab      ACTIVE | AWAITING_REVIEW | SUSPENDED | REJECTED
 *   plan     FREE | PRO
 *   docState COMPLETE | PENDING | EXPIRING
 *   search   matches business name or owner name
 */
@RestController
@RequestMapping("/api/admin/vendors")
@RequiredArgsConstructor
public class AdminVendorController {

    private final AdminVendorService adminVendorService;

    @GetMapping
    public AdminVendorDtos.VendorsPageResponse getVendors(
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) String plan,
            @RequestParam(required = false) String docState,
            @RequestParam(required = false) String search) {

        return adminVendorService.getVendorsPage(tab, plan, docState, search);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportVendors(
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) String plan,
            @RequestParam(required = false) String docState,
            @RequestParam(required = false) String search) {

        String csv = adminVendorService.exportCsv(tab, plan, docState, search);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vendors.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}