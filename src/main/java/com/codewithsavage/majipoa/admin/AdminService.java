package com.codewithsavage.majipoa.admin;

import com.codewithsavage.majipoa.rider.*;
import com.codewithsavage.majipoa.shared.model.*;
import com.codewithsavage.majipoa.shared.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminService {

    private final VendorOrderRepository vendorOrderRepo;
    private final VendorProfileRepository vendorProfileRepo;
    private final VendorDocumentRepository vendorDocumentRepo;
    private final UserRepository userRepo;
    private final CustomerProfileRepository customerProfileRepo;
    private final MpesaPaymentRepository mpesaPaymentRepo;
    private final RefundCaseRepository refundCaseRepo;
    private final PayoutRecordRepository payoutRecordRepo;
    private final RiderRepository riderRepo;
    private final DeliveryRepository deliveryRepo;
    private final PayoutRepository payoutRepo;

    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");

    // ─── Dashboard ────────────────────────────────────────────────────────────

    public AdminDtos.DashboardResponse getDashboard() {
        Instant startOfDay = LocalDate.now(NAIROBI).atStartOfDay(NAIROBI).toInstant();
        Instant now = Instant.now();

        long ordersToday = vendorOrderRepo.findAll().stream()
                .filter(o -> o.getPlacedAt() != null && o.getPlacedAt().isAfter(startOfDay))
                .count();

        long activeRiders = riderRepo.findByStatus(RiderStatus.AVAILABLE).size()
                + riderRepo.findByStatus(RiderStatus.BUSY).size();

        long pendingVerifications = vendorProfileRepo.findAll().stream()
                .filter(v -> v.getVerificationStatus() == VendorProfile.VerificationStatus.PENDING)
                .count();

        long openRefunds = refundCaseRepo.findByStatus(RefundCase.RefundStatus.OPEN).size();
        long unmatchedPayments = mpesaPaymentRepo.findByStatus(MpesaPayment.PaymentStatus.UNMATCHED).size();
        long totalVendors = vendorProfileRepo.count();
        long totalCustomers = customerProfileRepo.count();
        long totalRiders = riderRepo.count();

        AdminDtos.KpiDto kpis = new AdminDtos.KpiDto(
                ordersToday, activeRiders, pendingVerifications, openRefunds,
                unmatchedPayments, totalVendors, totalCustomers, totalRiders);

        // Orders per hour today (Nairobi time)
        List<AdminDtos.OrdersPerHourDto> ordersPerHour = vendorOrderRepo.findAll().stream()
                .filter(o -> o.getPlacedAt() != null && o.getPlacedAt().isAfter(startOfDay))
                .collect(Collectors.groupingBy(o ->
                        o.getPlacedAt().atZone(NAIROBI).getHour(), Collectors.counting()))
                .entrySet().stream()
                .map(e -> new AdminDtos.OrdersPerHourDto(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(AdminDtos.OrdersPerHourDto::hour))
                .toList();

        // Top vendors by orders today
        List<AdminDtos.TopVendorDto> topVendors = vendorOrderRepo.findAll().stream()
                .filter(o -> o.getPlacedAt() != null && o.getPlacedAt().isAfter(startOfDay))
                .collect(Collectors.groupingBy(o -> o.getVendor().getId(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> {
                    VendorProfile vp = vendorProfileRepo.findById(e.getKey()).orElse(null);
                    String name = vp != null ? vp.getBusinessName() : "Unknown";
                    return new AdminDtos.TopVendorDto(e.getKey(), name, e.getValue(), 0L);
                })
                .toList();

        // Demand gaps by area (areas with orders but few vendors)
        Map<String, Long> ordersByArea = vendorOrderRepo.findAll().stream()
                .filter(o -> o.getArea() != null)
                .collect(Collectors.groupingBy(VendorOrder::getArea, Collectors.counting()));
        List<AdminDtos.DemandGapDto> demandGaps = ordersByArea.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> new AdminDtos.DemandGapDto(e.getKey(), e.getValue(), 1L))
                .toList();

        // Attention items
        List<AdminDtos.AttentionItemDto> attentionItems = new ArrayList<>();
        Instant stuckThreshold = now.minus(30, ChronoUnit.MINUTES);
        long stuck = vendorOrderRepo.findAll().stream()
                .filter(o -> (o.getStatus() == VendorOrder.OrderStatus.NEW
                        || o.getStatus() == VendorOrder.OrderStatus.PREPARING)
                        && o.getPlacedAt() != null && o.getPlacedAt().isBefore(stuckThreshold))
                .count();
        if (stuck > 0) {
            attentionItems.add(new AdminDtos.AttentionItemDto("STUCK_ORDERS",
                    stuck + " order(s) stuck > 30 min", "/admin/orders?tab=stuck"));
        }
        if (pendingVerifications > 0) {
            attentionItems.add(new AdminDtos.AttentionItemDto("PENDING_VERIFICATION",
                    pendingVerifications + " pending verification(s)", "/admin/verification"));
        }
        if (openRefunds > 0) {
            attentionItems.add(new AdminDtos.AttentionItemDto("OPEN_REFUND",
                    openRefunds + " open refund case(s)", "/admin/refunds"));
        }
        if (unmatchedPayments > 0) {
            attentionItems.add(new AdminDtos.AttentionItemDto("UNMATCHED_PAYMENT",
                    unmatchedPayments + " unmatched payment(s)", "/admin/payments?tab=unmatched"));
        }

        return new AdminDtos.DashboardResponse(kpis, ordersPerHour, topVendors, demandGaps, attentionItems);
    }

    // ─── Orders ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminDtos.OrdersKpiResponse getOrdersKpis() {
        Instant stuckThreshold = Instant.now().minus(30, ChronoUnit.MINUTES);
        List<VendorOrder> all = vendorOrderRepo.findAll();
        long total = all.size();
        long pending = all.stream().filter(o -> o.getStatus() == VendorOrder.OrderStatus.NEW).count();
        long preparing = all.stream().filter(o -> o.getStatus() == VendorOrder.OrderStatus.PREPARING).count();
        long outForDelivery = all.stream().filter(o -> o.getStatus() == VendorOrder.OrderStatus.OUT_FOR_DELIVERY).count();
        long completed = all.stream().filter(o -> o.getStatus() == VendorOrder.OrderStatus.COMPLETED).count();
        long cancelled = all.stream().filter(o -> o.getStatus() == VendorOrder.OrderStatus.CANCELLED).count();
        long stuck = all.stream()
                .filter(o -> (o.getStatus() == VendorOrder.OrderStatus.NEW
                        || o.getStatus() == VendorOrder.OrderStatus.PREPARING)
                        && o.getPlacedAt() != null && o.getPlacedAt().isBefore(stuckThreshold))
                .count();
        return new AdminDtos.OrdersKpiResponse(total, pending, preparing, outForDelivery, completed, cancelled, stuck);
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.AdminOrderDto> getOrders(String tab, String search) {
        Instant stuckThreshold = Instant.now().minus(30, ChronoUnit.MINUTES);
        return vendorOrderRepo.findAll().stream()
                .filter(o -> {
                    if (tab == null || tab.isBlank()) return true;
                    return switch (tab.toLowerCase()) {
                        case "pending" -> o.getStatus() == VendorOrder.OrderStatus.NEW;
                        case "preparing" -> o.getStatus() == VendorOrder.OrderStatus.PREPARING;
                        case "out_for_delivery" -> o.getStatus() == VendorOrder.OrderStatus.OUT_FOR_DELIVERY;
                        case "completed" -> o.getStatus() == VendorOrder.OrderStatus.COMPLETED;
                        case "cancelled" -> o.getStatus() == VendorOrder.OrderStatus.CANCELLED;
                        case "stuck" -> (o.getStatus() == VendorOrder.OrderStatus.NEW
                                || o.getStatus() == VendorOrder.OrderStatus.PREPARING)
                                && o.getPlacedAt() != null && o.getPlacedAt().isBefore(stuckThreshold);
                        default -> true;
                    };
                })
                .filter(o -> search == null || search.isBlank()
                        || o.getOrderRef().toLowerCase().contains(search.toLowerCase())
                        || (o.getCustomerName() != null && o.getCustomerName().toLowerCase().contains(search.toLowerCase())))
                .map(this::toAdminOrderDto)
                .toList();
    }

    public AdminDtos.AdminOrderDto interveneOrder(String orderRef, AdminDtos.InterveneRequest req) {
        VendorOrder order = vendorOrderRepo.findByOrderRef(orderRef)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderRef));
        if ("cancel".equalsIgnoreCase(req.action())) {
            order.setStatus(VendorOrder.OrderStatus.CANCELLED);
        } else if ("force_complete".equalsIgnoreCase(req.action())) {
            order.setStatus(VendorOrder.OrderStatus.COMPLETED);
            order.setDeliveredAt(Instant.now());
        } else if ("reassign_rider".equalsIgnoreCase(req.action()) && req.riderName() != null) {
            order.setRiderName(req.riderName());
        }
        vendorOrderRepo.save(order);
        return toAdminOrderDto(order);
    }

    // ─── Customers ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminDtos.CustomersResponse getCustomers(String tab, String search, String area) {
        List<CustomerProfile> all = customerProfileRepo.findAll();
        List<AdminDtos.CustomerDto> filtered = all.stream()
                .filter(c -> {
                    if (tab == null || tab.isBlank()) return true;
                    return switch (tab.toLowerCase()) {
                        case "active" -> c.getStatus() == CustomerProfile.CustomerStatus.ACTIVE;
                        case "at_risk" -> c.getStatus() == CustomerProfile.CustomerStatus.AT_RISK;
                        case "blocked" -> c.getStatus() == CustomerProfile.CustomerStatus.BLOCKED;
                        default -> true;
                    };
                })
                .filter(c -> area == null || area.isBlank()
                        || (c.getArea() != null && c.getArea().equalsIgnoreCase(area)))
                .filter(c -> search == null || search.isBlank()
                        || (c.getName() != null && c.getName().toLowerCase().contains(search.toLowerCase()))
                        || (c.getPhone() != null && c.getPhone().contains(search)))
                .map(this::toCustomerDto)
                .toList();
        return new AdminDtos.CustomersResponse(filtered, filtered.size());
    }

    public AdminDtos.CustomerDto blockCustomer(Long id, AdminDtos.BlockRequest req) {
        CustomerProfile cp = customerProfileRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
        cp.setStatus(CustomerProfile.CustomerStatus.BLOCKED);
        cp.setNote(req.reason());
        cp.setBlockedAt(Instant.now());
        cp.setBlockedBy("admin");
        return toCustomerDto(customerProfileRepo.save(cp));
    }

    public AdminDtos.CustomerDto unblockCustomer(Long id) {
        CustomerProfile cp = customerProfileRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));
        cp.setStatus(CustomerProfile.CustomerStatus.ACTIVE);
        cp.setBlockedAt(null);
        cp.setBlockedBy(null);
        return toCustomerDto(customerProfileRepo.save(cp));
    }

    // ─── Vendors ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AdminDtos.AdminVendorDto> getVendors(String tab, String area, String plan,
                                                      String docState, String search) {
        return vendorProfileRepo.findAll().stream()
                .filter(v -> {
                    if (tab == null || tab.isBlank()) return true;
                    return switch (tab.toLowerCase()) {
                        case "pending" -> v.getVerificationStatus() == VendorProfile.VerificationStatus.PENDING;
                        case "approved" -> v.getVerificationStatus() == VendorProfile.VerificationStatus.APPROVED;
                        case "suspended" -> v.getVerificationStatus() == VendorProfile.VerificationStatus.SUSPENDED;
                        default -> true;
                    };
                })
                .filter(v -> plan == null || plan.isBlank()
                        || v.getPlan().name().equalsIgnoreCase(plan))
                .filter(v -> search == null || search.isBlank()
                        || v.getBusinessName().toLowerCase().contains(search.toLowerCase()))
                .map(this::toAdminVendorDto)
                .toList();
    }

    public AdminDtos.AdminVendorDto approveVendor(Long id) {
        VendorProfile vp = vendorProfileRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + id));
        vp.setVerificationStatus(VendorProfile.VerificationStatus.APPROVED);
        return toAdminVendorDto(vendorProfileRepo.save(vp));
    }

    public AdminDtos.AdminVendorDto suspendVendor(Long id, AdminDtos.SuspendRequest req) {
        VendorProfile vp = vendorProfileRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + id));
        vp.setVerificationStatus(VendorProfile.VerificationStatus.SUSPENDED);
        return toAdminVendorDto(vendorProfileRepo.save(vp));
    }

    public AdminDtos.AdminVendorDto reinstateVendor(Long id) {
        VendorProfile vp = vendorProfileRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + id));
        vp.setVerificationStatus(VendorProfile.VerificationStatus.APPROVED);
        return toAdminVendorDto(vendorProfileRepo.save(vp));
    }

    // ─── Riders ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminDtos.RidersResponse getRiders(String tab, String area, String search) {
        List<AdminDtos.AdminRiderDto> filtered = riderRepo.findAll().stream()
                .filter(r -> {
                    if (tab == null || tab.isBlank()) return true;
                    return switch (tab.toLowerCase()) {
                        case "available" -> r.getStatus() == RiderStatus.AVAILABLE;
                        case "busy" -> r.getStatus() == RiderStatus.BUSY;
                        case "offline" -> r.getStatus() == RiderStatus.OFFLINE;
                        case "suspended" -> r.isAdminSuspended();
                        case "unverified" -> !r.isVerified();
                        default -> true;
                    };
                })
                .filter(r -> search == null || search.isBlank()
                        || r.getFullName().toLowerCase().contains(search.toLowerCase())
                        || (r.getPhone() != null && r.getPhone().contains(search)))
                .map(this::toAdminRiderDto)
                .toList();
        return new AdminDtos.RidersResponse(filtered, filtered.size());
    }

    public AdminDtos.AdminRiderDto suspendRider(Long id, AdminDtos.RiderSuspendRequest req) {
        Rider rider = riderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rider not found: " + id));
        rider.setAdminSuspended(true);
        rider.setStatus(RiderStatus.OFFLINE);
        return toAdminRiderDto(riderRepo.save(rider));
    }

    public AdminDtos.AdminRiderDto reinstateRider(Long id) {
        Rider rider = riderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rider not found: " + id));
        rider.setAdminSuspended(false);
        return toAdminRiderDto(riderRepo.save(rider));
    }

    public AdminDtos.AdminRiderDto approveRider(Long id) {
        Rider rider = riderRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rider not found: " + id));
        rider.setVerified(true);
        return toAdminRiderDto(riderRepo.save(rider));
    }

    // ─── Nearby Riders ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AdminDtos.RiderSummaryDto> getNearbyRiders(String orderRef, double radiusKm) {
        VendorOrder order = vendorOrderRepo.findByOrderRef(orderRef)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderRef));
        VendorProfile vendor = order.getVendor();
        Double vLat = vendor.getLatitude();
        Double vLng = vendor.getLongitude();
        if (vLat == null || vLng == null) {
            return List.of();
        }
        return riderRepo.findByStatus(RiderStatus.AVAILABLE).stream()
                .filter(r -> r.getLatitude() != null && r.getLongitude() != null)
                .filter(r -> !r.isAdminSuspended())
                .map(r -> {
                    double dist = HaversineUtil.distanceKm(vLat, vLng, r.getLatitude(), r.getLongitude());
                    return new AdminDtos.RiderSummaryDto(r.getId(), r.getFullName(), r.getPhone(), dist);
                })
                .filter(dto -> dto.distanceKm() <= radiusKm)
                .sorted(Comparator.comparingDouble(AdminDtos.RiderSummaryDto::distanceKm))
                .toList();
    }

    // ─── Verification ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AdminDtos.VerificationQueueItem> getVerificationQueue() {
        return vendorProfileRepo.findAll().stream()
                .filter(v -> v.getVerificationStatus() == VendorProfile.VerificationStatus.PENDING)
                .map(v -> new AdminDtos.VerificationQueueItem(
                        v.getId(), v.getBusinessName(),
                        v.getVerificationStatus().name(), v.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminDtos.VerificationDetail getVerificationDetail(Long vendorId) {
        VendorProfile vp = vendorProfileRepo.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + vendorId));
        List<AdminDtos.DocDto> docs = vendorDocumentRepo.findByVendorId(vendorId).stream()
                .map(d -> new AdminDtos.DocDto(d.getId(), d.getName(),
                        d.getStatus().name(), d.getReference(), d.getNote()))
                .toList();
        return new AdminDtos.VerificationDetail(
                vp.getId(), vp.getBusinessName(), vp.getVerificationStatus().name(), docs, null);
    }

    public AdminDtos.VerificationDetail approveVerification(Long vendorId) {
        VendorProfile vp = vendorProfileRepo.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + vendorId));
        vp.setVerificationStatus(VendorProfile.VerificationStatus.APPROVED);
        vendorProfileRepo.save(vp);
        return getVerificationDetail(vendorId);
    }

    public AdminDtos.VerificationDetail rejectVerification(Long vendorId, AdminDtos.VerificationNoteRequest req) {
        VendorProfile vp = vendorProfileRepo.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + vendorId));
        vp.setVerificationStatus(VendorProfile.VerificationStatus.SUSPENDED);
        vendorProfileRepo.save(vp);
        return getVerificationDetail(vendorId);
    }

    public AdminDtos.VerificationDetail requestVerificationChanges(Long vendorId, AdminDtos.VerificationNoteRequest req) {
        VendorProfile vp = vendorProfileRepo.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + vendorId));
        // keep status as PENDING, just note the request
        vendorProfileRepo.save(vp);
        return getVerificationDetail(vendorId);
    }

    public AdminDtos.DocDto approveDocument(Long vendorId, Long docId) {
        VendorDocument doc = vendorDocumentRepo.findById(docId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + docId));
        doc.setStatus(VendorDocument.DocStatus.APPROVED);
        vendorDocumentRepo.save(doc);
        return new AdminDtos.DocDto(doc.getId(), doc.getName(), doc.getStatus().name(),
                doc.getReference(), doc.getNote());
    }

    public AdminDtos.DocDto queryDocument(Long vendorId, Long docId, AdminDtos.DocDecisionRequest req) {
        VendorDocument doc = vendorDocumentRepo.findById(docId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + docId));
        doc.setNote(req.note());
        vendorDocumentRepo.save(doc);
        return new AdminDtos.DocDto(doc.getId(), doc.getName(), doc.getStatus().name(),
                doc.getReference(), doc.getNote());
    }

    public AdminDtos.DocDto requestDocument(Long vendorId, AdminDtos.DocDecisionRequest req) {
        VendorProfile vp = vendorProfileRepo.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + vendorId));
        VendorDocument doc = new VendorDocument();
        doc.setVendor(vp);
        doc.setName(req.note() != null ? req.note() : "Requested document");
        doc.setStatus(VendorDocument.DocStatus.NOT_SUBMITTED);
        vendorDocumentRepo.save(doc);
        return new AdminDtos.DocDto(doc.getId(), doc.getName(), doc.getStatus().name(),
                doc.getReference(), doc.getNote());
    }

    // ─── Subscriptions ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminDtos.SubscriptionsResponse getSubscriptions(String tab, String search) {
        List<AdminDtos.SubscriptionDto> filtered = vendorProfileRepo.findAll().stream()
                .filter(v -> {
                    if (tab == null || tab.isBlank()) return true;
                    return v.getPlan().name().equalsIgnoreCase(tab);
                })
                .filter(v -> search == null || search.isBlank()
                        || v.getBusinessName().toLowerCase().contains(search.toLowerCase()))
                .map(v -> new AdminDtos.SubscriptionDto(
                        v.getId(), v.getBusinessName(), v.getPlan().name(), v.getCreatedAt()))
                .toList();
        return new AdminDtos.SubscriptionsResponse(filtered, filtered.size());
    }

    public AdminDtos.SubscriptionDto cancelSubscription(Long vendorId) {
        VendorProfile vp = vendorProfileRepo.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + vendorId));
        vp.setPlan(VendorProfile.SubscriptionPlan.FREE);
        vendorProfileRepo.save(vp);
        return new AdminDtos.SubscriptionDto(vp.getId(), vp.getBusinessName(),
                vp.getPlan().name(), vp.getCreatedAt());
    }

    public AdminDtos.SubscriptionDto upgradeSubscription(Long vendorId) {
        VendorProfile vp = vendorProfileRepo.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + vendorId));
        vp.setPlan(VendorProfile.SubscriptionPlan.PRO);
        vendorProfileRepo.save(vp);
        return new AdminDtos.SubscriptionDto(vp.getId(), vp.getBusinessName(),
                vp.getPlan().name(), vp.getCreatedAt());
    }

    // ─── Payouts ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminDtos.PayoutsResponse getPayouts(String tab, String recipient, String search) {
        // Combine vendor PayoutRecords and rider Payouts
        List<AdminDtos.AdminPayoutDto> dtos = new ArrayList<>();

        payoutRecordRepo.findAll().forEach(pr -> {
            String name = pr.getVendor() != null ? pr.getVendor().getBusinessName() : "Unknown";
            if ((search == null || search.isBlank() || name.toLowerCase().contains(search.toLowerCase()))
                    && (recipient == null || recipient.isBlank() || "vendor".equalsIgnoreCase(recipient))) {
                dtos.add(new AdminDtos.AdminPayoutDto(
                        pr.getId(), name, "VENDOR",
                        (pr.getPeriodStart() != null ? pr.getPeriodStart().toString() : "") + " – "
                                + (pr.getPeriodEnd() != null ? pr.getPeriodEnd().toString() : ""),
                        pr.getPaid(), "PAID", pr.getReference()));
            }
        });

        payoutRepo.findAll().forEach(p -> {
            String name = p.getRider() != null ? p.getRider().getFullName() : "Unknown";
            if ((search == null || search.isBlank() || name.toLowerCase().contains(search.toLowerCase()))
                    && (recipient == null || recipient.isBlank() || "rider".equalsIgnoreCase(recipient))) {
                dtos.add(new AdminDtos.AdminPayoutDto(
                        p.getId(), name, "RIDER",
                        (p.getPeriodStart() != null ? p.getPeriodStart().toString() : "") + " – "
                                + (p.getPeriodEnd() != null ? p.getPeriodEnd().toString() : ""),
                        p.getAmount() != null ? p.getAmount().longValue() : 0L,
                        p.getStatus().name(), p.getMpesaReference()));
            }
        });

        List<AdminDtos.AdminPayoutDto> filteredByTab = dtos.stream()
                .filter(dto -> tab == null || tab.isBlank()
                        || dto.status().equalsIgnoreCase(tab))
                .toList();

        AdminDtos.NextSettlementDto nextSettlement = new AdminDtos.NextSettlementDto(
                LocalDate.now().plusDays(7).toString(), 0L);

        return new AdminDtos.PayoutsResponse(filteredByTab, filteredByTab.size(), nextSettlement);
    }

    public void runSettlement() {
        // Placeholder — real implementation would batch-process pending payouts
    }

    public AdminDtos.AdminPayoutDto holdPayout(Long id) {
        Payout p = payoutRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Payout not found: " + id));
        p.setStatus(PayoutStatus.FAILED);
        payoutRepo.save(p);
        String name = p.getRider() != null ? p.getRider().getFullName() : "Unknown";
        return new AdminDtos.AdminPayoutDto(p.getId(), name, "RIDER", "", 0L, p.getStatus().name(), null);
    }

    public AdminDtos.AdminPayoutDto releasePayout(Long id) {
        Payout p = payoutRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Payout not found: " + id));
        p.setStatus(PayoutStatus.PENDING);
        payoutRepo.save(p);
        String name = p.getRider() != null ? p.getRider().getFullName() : "Unknown";
        return new AdminDtos.AdminPayoutDto(p.getId(), name, "RIDER", "", 0L, p.getStatus().name(), null);
    }

    // ─── Payments ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminDtos.PaymentsResponse getPayments(String tab, String search) {
        List<AdminDtos.PaymentDto> filtered = mpesaPaymentRepo.findAll().stream()
                .filter(p -> {
                    if (tab == null || tab.isBlank()) return true;
                    return p.getStatus().name().equalsIgnoreCase(tab);
                })
                .filter(p -> search == null || search.isBlank()
                        || (p.getMpesaCode() != null && p.getMpesaCode().toLowerCase().contains(search.toLowerCase()))
                        || (p.getPhone() != null && p.getPhone().contains(search)))
                .map(p -> new AdminDtos.PaymentDto(
                        p.getId(), p.getMpesaCode(), p.getPhone(), p.getAmount(),
                        p.getReceivedAt(), p.getMatchedOrderRef(),
                        p.getStatus().name(), p.getFailureReason()))
                .toList();

        long matched = mpesaPaymentRepo.findByStatus(MpesaPayment.PaymentStatus.MATCHED).size();
        long unmatched = mpesaPaymentRepo.findByStatus(MpesaPayment.PaymentStatus.UNMATCHED).size();
        long failed = mpesaPaymentRepo.findByStatus(MpesaPayment.PaymentStatus.FAILED).size();
        AdminDtos.MoneyLineDto moneyLine = new AdminDtos.MoneyLineDto(matched, unmatched, failed);

        return new AdminDtos.PaymentsResponse(filtered, filtered.size(), moneyLine);
    }

    public void reconcilePayments() {
        // Auto-match unmatched payments to orders by order ref if possible
        List<MpesaPayment> unmatched = mpesaPaymentRepo.findByStatus(MpesaPayment.PaymentStatus.UNMATCHED);
        for (MpesaPayment mp : unmatched) {
            if (mp.getMatchedOrderRef() != null) {
                vendorOrderRepo.findByOrderRef(mp.getMatchedOrderRef()).ifPresent(order -> {
                    mp.setStatus(MpesaPayment.PaymentStatus.MATCHED);
                    mpesaPaymentRepo.save(mp);
                });
            }
        }
    }

    // ─── Refunds ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminDtos.RefundsResponse getRefunds() {
        List<AdminDtos.RefundCaseDto> all = refundCaseRepo.findAllByOrderByOpenedAtDesc().stream()
                .map(this::toRefundCaseDto)
                .toList();
        long openCount = refundCaseRepo.findByStatus(RefundCase.RefundStatus.OPEN).size();
        long totalRefunded = refundCaseRepo.findByStatus(RefundCase.RefundStatus.APPROVED).stream()
                .mapToLong(RefundCase::getAmount).sum();
        return new AdminDtos.RefundsResponse(all, openCount, totalRefunded);
    }

    public AdminDtos.RefundCaseDto approveRefund(Long id, AdminDtos.ApproveRefundRequest req) {
        RefundCase rc = refundCaseRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Refund case not found: " + id));
        rc.setStatus(RefundCase.RefundStatus.APPROVED);
        rc.setMpesaRef(req.mpesaRef());
        rc.setBornBy(req.bornBy());
        rc.setResolvedAt(Instant.now());
        rc.setResolvedBy("admin");
        return toRefundCaseDto(refundCaseRepo.save(rc));
    }

    public AdminDtos.RefundCaseDto rejectRefund(Long id, AdminDtos.CaseActionDto req) {
        RefundCase rc = refundCaseRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Refund case not found: " + id));
        rc.setStatus(RefundCase.RefundStatus.REJECTED);
        rc.setResolvedAt(Instant.now());
        rc.setResolvedBy("admin");
        return toRefundCaseDto(refundCaseRepo.save(rc));
    }

    // ─── Mapper helpers ───────────────────────────────────────────────────────

    private AdminDtos.AdminOrderDto toAdminOrderDto(VendorOrder o) {
        List<AdminDtos.OrderLineDto> lines = o.getLines().stream()
                .map(l -> new AdminDtos.OrderLineDto(l.getProductName(), l.getQty(), l.getUnitPrice()))
                .toList();
        String vendorName = o.getVendor() != null ? o.getVendor().getBusinessName() : null;
        return new AdminDtos.AdminOrderDto(
                o.getId(), o.getOrderRef(), o.getCustomerName(), o.getCustomerPhone(),
                vendorName, o.getArea(), o.getStatus().name(), o.getPaymentStatus().name(),
                o.getRiderName(), o.getPlacedAt(), o.getAcceptedAt(), o.getDeliveredAt(), lines);
    }

    private AdminDtos.CustomerDto toCustomerDto(CustomerProfile c) {
        return new AdminDtos.CustomerDto(
                c.getId(), c.getUserId(), c.getName(), c.getPhone(), c.getArea(),
                c.getTotalOrders(), c.getTotalSpend(), c.getLastOrderAt(), c.getRatingGiven(),
                c.getStatus().name(), c.getNote(), c.getBlockedAt(), c.getBlockedBy(), c.getCreatedAt());
    }

    private AdminDtos.AdminVendorDto toAdminVendorDto(VendorProfile v) {
        return new AdminDtos.AdminVendorDto(
                v.getId(), v.getUser() != null ? v.getUser().getId() : null,
                v.getBusinessName(), v.getVerificationStatus().name(), v.getPlan().name(),
                v.getLatitude(), v.getLongitude(), v.getCreatedAt());
    }

    private AdminDtos.AdminRiderDto toAdminRiderDto(Rider r) {
        return new AdminDtos.AdminRiderDto(
                r.getId(), r.getFullName(), r.getPhone(), r.getEmail(),
                r.getVehicleType(), r.getPlateNumber(),
                r.isVerified(), r.isAdminSuspended(),
                r.getStatus().name(), r.getLatitude(), r.getLongitude());
    }

    private AdminDtos.RefundCaseDto toRefundCaseDto(RefundCase rc) {
        return new AdminDtos.RefundCaseDto(
                rc.getId(), rc.getOrderId(), rc.getCustomerName(), rc.getVendorName(),
                rc.getAmount(), rc.getTag(), rc.getDescription(), rc.getOpenedAt(),
                rc.getStatus().name(), rc.getBornBy(), rc.getMpesaRef(),
                rc.getResolvedAt(), rc.getResolvedBy());
    }
}
