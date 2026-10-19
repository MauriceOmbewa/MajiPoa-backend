package com.codewithsavage.majipoa.admin.vendor;

import com.codewithsavage.majipoa.shared.model.*;
import com.codewithsavage.majipoa.shared.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Read side of the admin Vendors page. Approve / suspend / reinstate stay in AdminService.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminVendorService {

    private final VendorProfileRepository vendorProfileRepo;
    private final VendorOrderRepository vendorOrderRepo;
    private final VendorDocumentRepository vendorDocumentRepo;

    // ─── Public API ──────────────────────────────────────────────────────────

    public AdminVendorDtos.VendorsPageResponse getVendorsPage(
            String tab, String plan, String docState, String search) {

        List<AdminVendorDtos.VendorRowDto> allRows = buildRows();

        // Tab counts ignore the other filters, like the "Active (214)" headers in the UI
        AdminVendorDtos.TabCountsDto counts = new AdminVendorDtos.TabCountsDto(
                countStatus(allRows, "ACTIVE"),
                countStatus(allRows, "AWAITING_REVIEW"),
                countStatus(allRows, "SUSPENDED"),
                countStatus(allRows, "REJECTED"));

        List<AdminVendorDtos.VendorRowDto> filtered = applyFilters(allRows, tab, plan, docState, search);

        return new AdminVendorDtos.VendorsPageResponse(filtered, filtered.size(), counts);
    }

    /** CSV for the Export button; respects the same filters as the table. */
    public String exportCsv(String tab, String plan, String docState, String search) {
        List<AdminVendorDtos.VendorRowDto> rows =
                applyFilters(buildRows(), tab, plan, docState, search);

        StringBuilder sb = new StringBuilder();
        sb.append("Vendor,Owner,Joined,Orders (30d),Rating,Documents,Plan,Status\n");
        for (AdminVendorDtos.VendorRowDto r : rows) {
            sb.append(csv(r.businessName())).append(',')
              .append(csv(r.ownerName())).append(',')
              .append(csv(r.joinedAt() != null ? r.joinedAt().toString() : "")).append(',')
              .append(r.orders30d()).append(',')
              .append(r.rating() != null ? r.rating() : "").append(',')
              .append(csv(r.documents().label())).append(',')
              .append(csv(r.plan())).append(',')
              .append(csv(r.status())).append('\n');
        }
        return sb.toString();
    }

    // ─── Row building (3 queries total, not 3 per vendor) ────────────────────

    private List<AdminVendorDtos.VendorRowDto> buildRows() {
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);

        Map<Long, Long> orders30d = vendorOrderRepo.findAll().stream()
                .filter(o -> o.getVendor() != null && o.getPlacedAt() != null && o.getPlacedAt().isAfter(since))
                .collect(Collectors.groupingBy(o -> o.getVendor().getId(), Collectors.counting()));

        Map<Long, List<VendorDocument>> docsByVendor = vendorDocumentRepo.findAll().stream()
                .filter(d -> d.getVendor() != null)
                .collect(Collectors.groupingBy(d -> d.getVendor().getId()));

        return vendorProfileRepo.findAll().stream()
                .map(v -> new AdminVendorDtos.VendorRowDto(
                        v.getId(),
                        v.getBusinessName(),
                        ownerName(v),
                        v.getCreatedAt(),
                        orders30d.getOrDefault(v.getId(), 0L),
                        rating(v),
                        summariseDocs(docsByVendor.getOrDefault(v.getId(), List.of())),
                        v.getPlan().name(),
                        statusOf(v)))
                .sorted(Comparator.comparingLong(AdminVendorDtos.VendorRowDto::orders30d).reversed())
                .toList();
    }

    // ─── Filtering ───────────────────────────────────────────────────────────

    private List<AdminVendorDtos.VendorRowDto> applyFilters(
            List<AdminVendorDtos.VendorRowDto> rows,
            String tab, String plan, String docState, String search) {

        String q = search == null ? "" : search.trim().toLowerCase();

        return rows.stream()
                .filter(r -> isBlank(tab) || r.status().equalsIgnoreCase(tab))
                .filter(r -> isBlank(plan) || r.plan().equalsIgnoreCase(plan))
                .filter(r -> isBlank(docState) || r.documents().state().equalsIgnoreCase(docState))
                .filter(r -> q.isEmpty()
                        || contains(r.businessName(), q)
                        || contains(r.ownerName(), q))
                .toList();
    }

    // ─── Mapping helpers ─────────────────────────────────────────────────────

    /** Maps the verification enum to the UI tabs. Uses name() so a future REJECTED value just works. */
    private String statusOf(VendorProfile v) {
        return switch (v.getVerificationStatus().name()) {
            case "APPROVED" -> "ACTIVE";
            case "PENDING" -> "AWAITING_REVIEW";
            case "SUSPENDED" -> "SUSPENDED";
            case "REJECTED" -> "REJECTED";
            default -> v.getVerificationStatus().name();
        };
    }

    private AdminVendorDtos.DocSummaryDto summariseDocs(List<VendorDocument> docs) {
        if (docs.isEmpty()) {
            return new AdminVendorDtos.DocSummaryDto("PENDING", "No documents");
        }
        long expiring = docs.stream().filter(d -> "EXPIRING".equals(d.getStatus().name())).count();
        if (expiring > 0) {
            return new AdminVendorDtos.DocSummaryDto("EXPIRING", expiring + " expiring");
        }
        Optional<VendorDocument> missing = docs.stream()
                .filter(d -> "NOT_SUBMITTED".equals(d.getStatus().name()))
                .findFirst();
        if (missing.isPresent()) {
            return new AdminVendorDtos.DocSummaryDto("PENDING", missing.get().getName() + " pending");
        }
        return new AdminVendorDtos.DocSummaryDto("COMPLETE", "Complete");
    }

    private long countStatus(List<AdminVendorDtos.VendorRowDto> rows, String status) {
        return rows.stream().filter(r -> r.status().equals(status)).count();
    }

    private String ownerName(VendorProfile v) {
        return v.getUser() != null ? v.getUser().getName() : null;   // AppUser#getName()
    }

    private Double rating(VendorProfile v) {
        return null;   // TODO: no rating field yet; return v.getRating() or an average from reviews
    }

    // ─── Small utils ─────────────────────────────────────────────────────────

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean contains(String value, String lowerQuery) {
        return value != null && value.toLowerCase().contains(lowerQuery);
    }

    private static String csv(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}