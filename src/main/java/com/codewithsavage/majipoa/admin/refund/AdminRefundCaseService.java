package com.codewithsavage.majipoa.admin.refund;

import com.codewithsavage.majipoa.admin.refund.RefundDtos.*;
import com.codewithsavage.majipoa.shared.model.RefundCase;
import com.codewithsavage.majipoa.shared.model.RefundCase.RefundStatus;
import com.codewithsavage.majipoa.shared.repository.RefundCaseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/**
 * Read model and decisions behind the admin Refunds and disputes page.
 *
 * Waiting on you = every OPEN case. Settled = APPROVED or REJECTED, newest first.
 * An APPROVED case without an M-Pesa reference shows as "Processing" until finance adds the reference.
 */
@Service
@Transactional
public class AdminRefundCaseService {

    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    private static final int SETTLED_LIMIT = 10;

    private enum Action {
        REFUND_FULL, REFUND_REPLACE, REFUND_CREDIT, ASK_VENDOR, HOLD_BATCH, REASSIGN, REJECT
    }

    private final RefundCaseRepository cases;
    private final RefundCaseActionRepository actions;
    private final RefundOrderStatsRepository orders;

    public AdminRefundCaseService(RefundCaseRepository cases,
                                  RefundCaseActionRepository actions,
                                  RefundOrderStatsRepository orders) {
        this.cases = cases;
        this.actions = actions;
        this.orders = orders;
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public RefundPageResponse overview() {
        List<RefundCase> all = cases.findAllByOrderByOpenedAtDesc();
        Map<Long, RefundCaseAction> lastByCase = lastActions(all);

        List<RefundCaseView> waiting = all.stream()
                .filter(c -> c.getStatus() == RefundStatus.OPEN)
                .map(c -> view(c, lastByCase.get(c.getId())))
                .toList();

        List<RefundCaseView> settled = all.stream()
                .filter(c -> c.getStatus() != RefundStatus.OPEN)
                .sorted(Comparator.comparing((RefundCase c) ->
                        c.getResolvedAt() != null ? c.getResolvedAt() : c.getOpenedAt()).reversed())
                .limit(SETTLED_LIMIT)
                .map(c -> view(c, lastByCase.get(c.getId())))
                .toList();

        return new RefundPageResponse(waiting, settled, stats(all));
    }

    private RefundStats stats(List<RefundCase> all) {
        Instant monthStart = LocalDate.now(NAIROBI).withDayOfMonth(1).atStartOfDay(NAIROBI).toInstant();

        long ordersThisMonth = orders.countByPlacedAtGreaterThanEqual(monthStart);
        long casesThisMonth = all.stream()
                .filter(c -> c.getOpenedAt() != null && !c.getOpenedAt().isBefore(monthStart))
                .count();
        double rate = ordersThisMonth == 0 ? 0 : round1(100.0 * casesThisMonth / ordersThisMonth);

        List<RefundCase> approvedThisMonth = all.stream()
                .filter(c -> c.getStatus() == RefundStatus.APPROVED
                        && c.getResolvedAt() != null && !c.getResolvedAt().isBefore(monthStart))
                .toList();

        // Ignore rows whose resolved time is before their opened time (bad data)
        OptionalDouble avg = all.stream()
                .filter(c -> c.getStatus() != RefundStatus.OPEN
                        && c.getResolvedAt() != null && c.getOpenedAt() != null
                        && !c.getResolvedAt().isBefore(monthStart)
                        && !c.getResolvedAt().isBefore(c.getOpenedAt()))
                .mapToLong(c -> Duration.between(c.getOpenedAt(), c.getResolvedAt()).toMinutes())
                .average();

        return new RefundStats(
                rate,
                borneBy(approvedThisMonth, "vendor"),
                borneBy(approvedThisMonth, "rider"),
                borneBy(approvedThisMonth, "platform"),
                avg.isPresent() ? Math.round(avg.getAsDouble()) : null);
    }

    private long borneBy(List<RefundCase> list, String party) {
        return list.stream()
                .filter(c -> party.equalsIgnoreCase(c.getBornBy()))
                .mapToLong(RefundCase::getAmount)
                .sum();
    }

    private Map<Long, RefundCaseAction> lastActions(List<RefundCase> all) {
        if (all.isEmpty()) return Map.of();
        List<Long> ids = all.stream().map(RefundCase::getId).toList();
        return actions.findByRefundCaseIdInOrderByCreatedAtAsc(ids).stream()
                .collect(Collectors.toMap(RefundCaseAction::getRefundCaseId, a -> a, (a, b) -> b));
    }

    // -------------------------------------------------------------- decisions

    public RefundCaseView decide(Long id, DecisionRequest req, String admin) {
        RefundCase c = find(id);
        if (c.getStatus() != RefundStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This case is already settled.");
        }

        Action action = parseAction(req.action());
        String bornBy = null;

        switch (action) {
            case REFUND_FULL, REFUND_REPLACE, REFUND_CREDIT -> {
                bornBy = resolveBornBy(req.bornBy(), c.getTag());
                c.setStatus(RefundStatus.APPROVED);
                c.setBornBy(bornBy);
                c.setResolvedAt(Instant.now());
                c.setResolvedBy(admin);
            }
            case REJECT -> {
                c.setStatus(RefundStatus.REJECTED);
                c.setResolvedAt(Instant.now());
                c.setResolvedBy(admin);
            }
            case ASK_VENDOR, HOLD_BATCH, REASSIGN -> {
                // The case stays open; the action is recorded below.
            }
        }

        cases.save(c);
        RefundCaseAction entry = log(id, action.name(), bornBy, req.note(), admin);
        return view(c, entry);
    }

    public RefundCaseView addMpesaRef(Long id, String mpesaRef, String admin) {
        RefundCase c = find(id);
        if (c.getStatus() != RefundStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only approved refunds take an M-Pesa reference.");
        }
        c.setMpesaRef(mpesaRef.trim());
        cases.save(c);
        RefundCaseAction entry = log(id, "ADD_MPESA_REF", null, mpesaRef.trim(), admin);
        return view(c, entry);
    }

    // ---------------------------------------------------------------- helpers

    private RefundCase find(Long id) {
        return cases.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund case not found: " + id));
    }

    private Action parseAction(String raw) {
        try {
            return Action.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown action: " + raw);
        }
    }

    /** Who bears the cost: the request wins, otherwise it is chosen from the case tag. */
    private String resolveBornBy(String requested, String tag) {
        if (requested != null && !requested.isBlank()) {
            String r = requested.trim().toLowerCase();
            if (r.equals("vendor") || r.equals("rider") || r.equals("platform")) {
                return Character.toUpperCase(r.charAt(0)) + r.substring(1);
            }
        }
        String t = tag == null ? "" : tag.toLowerCase();
        if (t.contains("damage") || t.contains("transit") || t.contains("failed delivery")) return "Rider";
        if (t.contains("duplicate") || t.contains("payment") || t.contains("routing")
                || t.contains("goodwill")) return "Platform";
        return "Vendor";
    }

    private RefundCaseAction log(Long caseId, String action, String bornBy, String note, String admin) {
        RefundCaseAction a = new RefundCaseAction();
        a.setRefundCaseId(caseId);
        a.setAction(action);
        a.setBornBy(bornBy);
        a.setNote(note);
        a.setPerformedBy(admin);
        return actions.save(a);
    }

    private RefundCaseView view(RefundCase c, RefundCaseAction last) {
        return new RefundCaseView(
                c.getId(),
                "#SUP-" + (4000 + c.getId()),
                c.getOrderId(),
                c.getCustomerName(),
                c.getVendorName(),
                c.getAmount(),
                c.getTag(),
                c.getDescription(),
                c.getOpenedAt(),
                c.getStatus().name(),
                c.getBornBy(),
                c.getMpesaRef(),
                c.getResolvedAt(),
                last == null ? null : last.getAction(),
                last == null ? null : last.getCreatedAt());
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}