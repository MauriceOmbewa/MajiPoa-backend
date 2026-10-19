package com.codewithsavage.majipoa.admin.payment;

import com.codewithsavage.majipoa.shared.model.MpesaPayment;
import com.codewithsavage.majipoa.shared.model.MpesaPayment.PaymentStatus;
import com.codewithsavage.majipoa.shared.model.RefundCase;
import com.codewithsavage.majipoa.shared.model.RefundCase.RefundStatus;
import com.codewithsavage.majipoa.shared.repository.MpesaPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin Payments page, built on MpesaPayment and RefundCase.
 *
 * Payment status meaning:
 *   MATCHED   -> money received and linked to an order
 *   UNMATCHED -> money received, no order linked yet   (the "Unreconciled" tab)
 *   FAILED    -> payment attempt failed / abandoned    (the "Failed" tab)
 *
 * The "Refunds" tab lists APPROVED refund cases.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPaymentService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Nairobi");
    private static final Duration RECOVERY_WINDOW = Duration.ofHours(1);

    private final MpesaPaymentRepository paymentRepo;
    private final PaymentRefundRepository refundRepo;

    // ─── Table ───────────────────────────────────────────────────────────────

    public AdminPaymentDtos.PaymentsPageResponse getPage(String tab, String search) {
        List<MpesaPayment> all = paymentRepo.findAllByOrderByReceivedAtDesc();
        List<RefundCase> refunds = refundRepo.findByStatusOrderByResolvedAtDesc(RefundStatus.APPROVED);

        AdminPaymentDtos.TabCountsDto counts = new AdminPaymentDtos.TabCountsDto(
                countStatus(all, PaymentStatus.UNMATCHED),
                all.size(),
                countStatus(all, PaymentStatus.FAILED),
                refunds.size());

        String t = tab == null ? "" : tab.trim().toUpperCase();

        List<AdminPaymentDtos.PaymentRowDto> rows;
        if (t.equals("REFUNDS")) {
            rows = refunds.stream()
                    .map(this::toRefundRow)
                    .filter(r -> matchesSearch(r, search))
                    .toList();
        } else {
            rows = all.stream()
                    .filter(p -> matchesTab(p, t))
                    .map(this::toRow)
                    .filter(r -> matchesSearch(r, search))
                    .toList();
        }

        return new AdminPaymentDtos.PaymentsPageResponse(rows, rows.size(), counts);
    }

    // ─── KPI cards ───────────────────────────────────────────────────────────

    public AdminPaymentDtos.SummaryDto getSummary() {
        Instant startOfToday = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
        List<MpesaPayment> all = paymentRepo.findAll();

        List<MpesaPayment> today = all.stream()
                .filter(p -> p.getReceivedAt() != null && !p.getReceivedAt().isBefore(startOfToday))
                .toList();

        // Money actually received = everything that did not fail
        List<MpesaPayment> receivedToday = today.stream()
                .filter(p -> p.getStatus() != PaymentStatus.FAILED)
                .toList();

        List<RefundCase> refundedToday = refundRepo.findByStatusOrderByResolvedAtDesc(RefundStatus.APPROVED)
                .stream()
                .filter(r -> r.getResolvedAt() != null && !r.getResolvedAt().isBefore(startOfToday))
                .toList();

        return new AdminPaymentDtos.SummaryDto(
                receivedToday.stream().mapToLong(MpesaPayment::getAmount).sum(),
                receivedToday.size(),
                today.stream().filter(p -> p.getStatus() == PaymentStatus.FAILED).count(),
                countStatus(all, PaymentStatus.UNMATCHED),
                refundedToday.stream().mapToLong(RefundCase::getAmount).sum(),
                refundedToday.size());
    }

    // ─── Failures by reason (last 7 days) ────────────────────────────────────

    public List<AdminPaymentDtos.FailureReasonDto> getFailures() {
        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
        List<MpesaPayment> all = paymentRepo.findAll();

        List<MpesaPayment> failed = all.stream()
                .filter(p -> p.getStatus() == PaymentStatus.FAILED)
                .filter(p -> p.getReceivedAt() != null && p.getReceivedAt().isAfter(since))
                .toList();
        if (failed.isEmpty()) return List.of();

        // Successful payments by phone, used to detect a retry that worked
        Map<String, List<MpesaPayment>> paidByPhone = all.stream()
                .filter(p -> p.getStatus() != PaymentStatus.FAILED && p.getPhone() != null)
                .collect(Collectors.groupingBy(p -> normalisePhone(p.getPhone())));

        Map<String, List<MpesaPayment>> byReason = failed.stream()
                .collect(Collectors.groupingBy(this::reasonOf));

        long total = failed.size();

        return byReason.entrySet().stream()
                .map(e -> {
                    List<MpesaPayment> group = e.getValue();
                    long recovered = group.stream().filter(f -> recovered(f, paidByPhone)).count();
                    return new AdminPaymentDtos.FailureReasonDto(
                            e.getKey(),
                            group.size(),
                            percent(group.size(), total),
                            percent(recovered, group.size()));
                })
                .sorted(Comparator.comparingLong(AdminPaymentDtos.FailureReasonDto::count).reversed())
                .toList();
    }

    // ─── Match action ────────────────────────────────────────────────────────

    @Transactional
    public AdminPaymentDtos.PaymentRowDto match(Long id, String orderRef) {
        if (orderRef == null || orderRef.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderRef is required");
        }
        MpesaPayment p = paymentRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));

        if (p.getStatus() != PaymentStatus.UNMATCHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only unmatched payments can be matched");
        }

        // TODO: verify the order exists (look up by orderRef in the VendorOrder repository)
        p.setMatchedOrderRef(orderRef.trim());
        p.setStatus(PaymentStatus.MATCHED);
        p.setFailureReason(null);
        return toRow(paymentRepo.save(p));
    }

    // ─── Row mapping ─────────────────────────────────────────────────────────

    private AdminPaymentDtos.PaymentRowDto toRow(MpesaPayment p) {
        return new AdminPaymentDtos.PaymentRowDto(
                p.getId(),
                p.getMpesaCode(),
                p.getReceivedAt(),
                p.getPhone(),
                p.getAmount(),
                p.getMatchedOrderRef(),
                p.getStatus().name(),
                labelOf(p));
    }

    private AdminPaymentDtos.PaymentRowDto toRefundRow(RefundCase r) {
        String code = r.getMpesaRef() != null && !r.getMpesaRef().isBlank()
                ? r.getMpesaRef() : "RF-" + r.getId();
        return new AdminPaymentDtos.PaymentRowDto(
                r.getId(),
                code,
                r.getResolvedAt(),
                null,                    // RefundCase doesn't store a phone number
                r.getAmount(),
                r.getOrderId(),
                "REFUNDED",
                "Refunded");
    }

    private String labelOf(MpesaPayment p) {
        if (p.getFailureReason() != null && !p.getFailureReason().isBlank()) {
            return p.getFailureReason();
        }
        return switch (p.getStatus()) {
            case MATCHED -> "Successful";
            case UNMATCHED -> "No matching order";
            case FAILED -> "Failed";
        };
    }

    private String reasonOf(MpesaPayment p) {
        return p.getFailureReason() == null || p.getFailureReason().isBlank()
                ? "Unknown" : p.getFailureReason();
    }

    // ─── Filtering ───────────────────────────────────────────────────────────

    private boolean matchesTab(MpesaPayment p, String upperTab) {
        return switch (upperTab) {
            case "UNRECONCILED" -> p.getStatus() == PaymentStatus.UNMATCHED;
            case "FAILED" -> p.getStatus() == PaymentStatus.FAILED;
            default -> true;   // ALL (and anything else)
        };
    }

    private boolean matchesSearch(AdminPaymentDtos.PaymentRowDto r, String search) {
        if (search == null || search.isBlank()) return true;
        String q = strip(search);
        return strip(r.code()).contains(q)
                || strip(r.phone()).contains(q)
                || strip(r.matchedOrder()).contains(q);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private boolean recovered(MpesaPayment failed, Map<String, List<MpesaPayment>> paidByPhone) {
        if (failed.getPhone() == null || failed.getReceivedAt() == null) return false;
        Instant from = failed.getReceivedAt();
        Instant to = from.plus(RECOVERY_WINDOW);
        return paidByPhone.getOrDefault(normalisePhone(failed.getPhone()), List.of()).stream()
                .anyMatch(ok -> ok.getReceivedAt() != null
                        && ok.getReceivedAt().isAfter(from)
                        && !ok.getReceivedAt().isAfter(to));
    }

    private long countStatus(List<MpesaPayment> all, PaymentStatus s) {
        return all.stream().filter(p -> p.getStatus() == s).count();
    }

    private static int percent(long part, long whole) {
        return whole == 0 ? 0 : (int) Math.round(part * 100.0 / whole);
    }

    private static String strip(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("\\s+", "");
    }

    private static String normalisePhone(String phone) {
        return phone.replaceAll("\\s+", "");
    }
}