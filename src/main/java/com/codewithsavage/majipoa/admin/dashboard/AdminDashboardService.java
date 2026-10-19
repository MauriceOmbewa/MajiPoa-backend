package com.codewithsavage.majipoa.admin.dashboard;

import com.codewithsavage.majipoa.Customer.model.Customer;
import com.codewithsavage.majipoa.Customer.model.Order;
import com.codewithsavage.majipoa.Customer.repository.CustomerRepository;
import com.codewithsavage.majipoa.admin.AdminDtos.AttentionItemDto;
import com.codewithsavage.majipoa.admin.AdminDtos.DashboardResponse;
import com.codewithsavage.majipoa.admin.AdminDtos.DemandGapDto;
import com.codewithsavage.majipoa.admin.AdminDtos.KpiDto;
import com.codewithsavage.majipoa.admin.AdminDtos.OrdersPerHourDto;
import com.codewithsavage.majipoa.admin.AdminDtos.TopVendorDto;
import com.codewithsavage.majipoa.admin.order.AdminMpesaPaymentRepository;
import com.codewithsavage.majipoa.admin.order.AdminOrderRepository;
import com.codewithsavage.majipoa.admin.order.AdminVendorOrderRepository;
import com.codewithsavage.majipoa.admin.order.AdminVendorRepository;
import com.codewithsavage.majipoa.rider.Rider;
import com.codewithsavage.majipoa.rider.RiderRepository;
import com.codewithsavage.majipoa.rider.RiderStatus;
import com.codewithsavage.majipoa.shared.model.MpesaPayment;
import com.codewithsavage.majipoa.shared.model.RefundCase;
import com.codewithsavage.majipoa.shared.model.VendorOrder;
import com.codewithsavage.majipoa.shared.model.VendorProfile;
import com.codewithsavage.majipoa.shared.repository.RefundCaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Numbers for the admin Platform dashboard. Orders are read the same way the admin Orders page
 * reads them (customer orders plus the vendor's copy), so the two screens agree.
 */
@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

    /** Business day boundaries. */
    private static final ZoneId ZONE = ZoneId.of("Africa/Nairobi");
    /** Hibernate stores LocalDateTime values in the JVM's zone, so convert with the same one. */
    private static final ZoneId DB_ZONE = ZoneId.systemDefault();

    private static final Duration STUCK_AFTER = Duration.ofHours(2);
    private static final int TOP_VENDORS = 5;
    private static final int DEMAND_AREAS = 10;
    private static final int DEMAND_DAYS = 30;

    private final AdminOrderRepository orders;
    private final AdminVendorOrderRepository vendorOrders;
    private final AdminVendorRepository vendors;
    private final AdminMpesaPaymentRepository payments;
    private final RefundCaseRepository refunds;
    private final RiderRepository riders;
    private final CustomerRepository customers;

    public AdminDashboardService(AdminOrderRepository orders,
                                 AdminVendorOrderRepository vendorOrders,
                                 AdminVendorRepository vendors,
                                 AdminMpesaPaymentRepository payments,
                                 RefundCaseRepository refunds,
                                 RiderRepository riders,
                                 CustomerRepository customers) {
        this.orders = orders;
        this.vendorOrders = vendorOrders;
        this.vendors = vendors;
        this.payments = payments;
        this.refunds = refunds;
        this.riders = riders;
        this.customers = customers;
    }

    public DashboardResponse dashboard() {
        Instant now = Instant.now();
        Instant dayStart = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();

        List<Order> all = orders.findAllWithDetails();
        List<Order> today = all.stream().filter(o -> !placed(o, now).isBefore(dayStart)).toList();
        List<VendorProfile> vendorList = vendors.findAll();
        List<Rider> riderList = riders.findAll();

        Map<String, VendorOrder> vendorOrderByRef = all.isEmpty() ? Map.of()
                : vendorOrders.findByOrderRefIn(all.stream().map(Order::getOrderId).toList())
                        .stream()
                        .collect(Collectors.toMap(VendorOrder::getOrderRef, v -> v, (a, b) -> a));

        // ---- KPIs
        long pendingVerifications = vendorList.stream()
                .filter(v -> v.getVerificationStatus() == VendorProfile.VerificationStatus.PENDING).count();
        long openRefunds = refunds.findByStatus(RefundCase.RefundStatus.OPEN).size();
        long unmatchedPayments = payments.findAll().stream()
                .filter(p -> p.getStatus() == MpesaPayment.PaymentStatus.UNMATCHED).count();
        long activeRiders = riderList.stream().filter(r -> r.getStatus() != RiderStatus.OFFLINE).count();

        KpiDto kpis = new KpiDto(
                today.size(),
                activeRiders,
                pendingVerifications,
                openRefunds,
                unmatchedPayments,
                vendorList.size(),
                customers.count(),
                riderList.size());

        return new DashboardResponse(
                kpis,
                ordersPerHour(today, now),
                topVendors(today),
                demandGaps(all, vendorList, now),
                attention(all, vendorOrderByRef, now,
                        pendingVerifications, openRefunds, unmatchedPayments));
    }

    // ------------------------------------------------------------ orders per hour

    /** One bucket for every hour of today (Nairobi time), 0 to 23. */
    private List<OrdersPerHourDto> ordersPerHour(List<Order> today, Instant now) {
        long[] byHour = new long[24];
        for (Order o : today) {
            byHour[placed(o, now).atZone(ZONE).getHour()]++;
        }
        return IntStream.range(0, 24).mapToObj(h -> new OrdersPerHourDto(h, byHour[h])).toList();
    }

    // ---------------------------------------------------------------- top vendors

    /** Vendors with the most orders today. Cancelled and refunded orders don't count. */
    private List<TopVendorDto> topVendors(List<Order> today) {
        Map<Long, List<Order>> byVendor = today.stream()
                .filter(o -> o.getVendor() != null && !isCancelledOrRefunded(o))
                .collect(Collectors.groupingBy(o -> o.getVendor().getId()));

        return byVendor.values().stream()
                .map(list -> {
                    VendorProfile v = list.get(0).getVendor();
                    return new TopVendorDto(v.getId(), v.getBusinessName(), list.size(),
                            list.stream().mapToLong(this::value).sum());
                })
                .sorted(Comparator.comparingLong(TopVendorDto::ordersToday)
                        .thenComparingLong(TopVendorDto::revenue).reversed())
                .limit(TOP_VENDORS)
                .toList();
    }

    // ----------------------------------------------------------------- demand gap

    /**
     * Orders per area over the last 30 days against the approved vendors that list that area.
     * An area with orders and no vendors is the clearest recruitment target.
     */
    private List<DemandGapDto> demandGaps(List<Order> all, List<VendorProfile> vendorList, Instant now) {
        Instant since = now.minus(Duration.ofDays(DEMAND_DAYS));

        Map<String, Long> ordersByArea = new HashMap<>();
        Map<String, String> display = new HashMap<>();
        for (Order o : all) {
            if (placed(o, now).isBefore(since)) continue;
            String area = area(o);
            if (area == null) continue;
            String key = area.toLowerCase();
            display.putIfAbsent(key, area);
            ordersByArea.merge(key, 1L, Long::sum);
        }

        Map<String, Long> vendorsByArea = new HashMap<>();
        for (VendorProfile v : vendorList) {
            if (v.getVerificationStatus() != VendorProfile.VerificationStatus.APPROVED
                    || v.getAreas() == null) continue;
            Set<String> served = Arrays.stream(v.getAreas().split(","))
                    .map(s -> s.trim().toLowerCase())
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
            served.forEach(a -> vendorsByArea.merge(a, 1L, Long::sum));
        }

        return ordersByArea.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(DEMAND_AREAS)
                .map(e -> new DemandGapDto(display.get(e.getKey()), e.getValue(),
                        vendorsByArea.getOrDefault(e.getKey(), 0L)))
                .toList();
    }

    // ------------------------------------------------------------ needs attention

    /** Only things with something to do; the frontend shows each label as it is. */
    private List<AttentionItemDto> attention(List<Order> all,
                                             Map<String, VendorOrder> vendorOrderByRef,
                                             Instant now,
                                             long pendingVerifications,
                                             long openRefunds,
                                             long unmatchedPayments) {
        long stuck = all.stream()
                .filter(o -> isOpen(o, vendorOrderByRef.get(o.getOrderId())))
                .filter(o -> Duration.between(placed(o, now), now).compareTo(STUCK_AFTER) > 0)
                .count();
        long waitingForVendor = all.stream()
                .filter(o -> o.getVendor() == null && isOpen(o, vendorOrderByRef.get(o.getOrderId())))
                .count();

        List<AttentionItemDto> items = new ArrayList<>();
        add(items, pendingVerifications, "verification",
                " vendor application awaiting review", " vendor applications awaiting review", "/verification");
        add(items, openRefunds, "refunds",
                " refund case open", " refund cases open", "/refunds");
        add(items, unmatchedPayments, "payments",
                " M-Pesa payment not matched to an order", " M-Pesa payments not matched to an order", "/payments");
        add(items, waitingForVendor, "orders",
                " order waiting for a vendor", " orders waiting for a vendor", "/orders");
        add(items, stuck, "orders",
                " order open over 2 hours", " orders open over 2 hours", "/orders");
        return items;
    }

    private void add(List<AttentionItemDto> items, long count, String type,
                     String singular, String plural, String link) {
        if (count > 0) {
            items.add(new AttentionItemDto(type, count + (count == 1 ? singular : plural), link));
        }
    }

    // -------------------------------------------------------------------- helpers

    private Instant placed(Order o, Instant fallback) {
        return o.getCreatedAt() != null ? toInstant(o.getCreatedAt()) : fallback;
    }

    private Instant toInstant(LocalDateTime t) {
        return t.atZone(DB_ZONE).toInstant();
    }

    private String area(Order o) {
        Customer c = o.getCustomer();
        var a = c == null ? null : c.getAddress();
        return a == null || a.getArea() == null || a.getArea().isBlank() ? null : a.getArea().trim();
    }

    private long value(Order o) {
        if (o.getTotalAmount() != null) return o.getTotalAmount().longValue();
        return o.getItems().stream()
                .filter(i -> i.getPrice() != null && i.getQuantity() != null)
                .mapToLong(i -> i.getPrice().longValue() * i.getQuantity())
                .sum();
    }

    private String statusName(Order o) {
        return o.getStatus() == null ? "PENDING" : o.getStatus().name();
    }

    private boolean isCancelledOrRefunded(Order o) {
        String s = statusName(o);
        return "CANCELLED".equals(s) || "CANCELED".equals(s) || "REFUNDED".equals(s);
    }

    /** Not delivered, cancelled or refunded: the same "open" idea as the Orders page. */
    private boolean isOpen(Order o, VendorOrder vo) {
        String s = statusName(o);
        boolean delivered = "COMPLETED".equals(s) || "DELIVERED".equals(s)
                || (vo != null && vo.getStatus() == VendorOrder.OrderStatus.COMPLETED);
        return !delivered && !isCancelledOrRefunded(o);
    }
}