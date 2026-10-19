package com.codewithsavage.majipoa.admin.order;

import com.codewithsavage.majipoa.Customer.model.Customer;
import com.codewithsavage.majipoa.Customer.model.Order;
import com.codewithsavage.majipoa.Customer.model.OrderItem;
import com.codewithsavage.majipoa.admin.order.AdminOrderDtos.*;
import com.codewithsavage.majipoa.shared.model.MpesaPayment;
import com.codewithsavage.majipoa.shared.model.OrderLine;
import com.codewithsavage.majipoa.shared.model.VendorOrder;
import com.codewithsavage.majipoa.shared.model.VendorProfile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read model and dispatch actions behind the admin Orders page, built from customer
 * orders (Orders_Table) and the vendor's copy of each dispatched order (VendorOrder).
 *
 * Flow:
 *   1. Customer places an order  -> Orders_Table row, no vendor            => "new" tab
 *   2. Admin dispatches          -> order.vendor set, VendorOrder (NEW) created for that vendor
 *   3. Vendor accepts/prepares/delivers -> only the VendorOrder changes; this service reads
 *      its status, so the admin page follows what the vendor does
 *   4. Vendor declines           -> order returns to the "new" tab for re-dispatch
 *
 * Tabs: new, stuck, live, completed, cancelled, refunded.
 */
@Service
@Transactional(readOnly = true)
public class AdminOrderService {

    private static final Duration STUCK_AFTER = Duration.ofHours(2);
    private static final Duration NOT_DISPATCHED_AFTER = Duration.ofMinutes(15);
    private static final Duration NOT_ACCEPTED_AFTER = Duration.ofMinutes(30);

    /** Business day boundaries (today, this week). */
    private static final ZoneId ZONE = ZoneId.of("Africa/Nairobi");
    /** Hibernate stores LocalDateTime values in the JVM's zone, so convert with the same one. */
    private static final ZoneId DB_ZONE = ZoneId.systemDefault();

    private final AdminOrderRepository orders;
    private final AdminMpesaPaymentRepository payments;
    private final AdminVendorRepository vendors;
    private final AdminVendorOrderRepository vendorOrders;

    public AdminOrderService(AdminOrderRepository orders,
                             AdminMpesaPaymentRepository payments,
                             AdminVendorRepository vendors,
                             AdminVendorOrderRepository vendorOrders) {
        this.orders = orders;
        this.payments = payments;
        this.vendors = vendors;
        this.vendorOrders = vendorOrders;
    }

    // ----------------------------------------------------------------- overview

    public AdminOrdersResponse overview() {
        Instant now = Instant.now();
        List<Order> all = orders.findAllWithDetails();
        List<String> ids = all.stream().map(Order::getOrderId).toList();

        Map<String, String> mpesaByOrder = all.isEmpty() ? Map.of()
                : payments.findByMatchedOrderRefIn(ids)
                        .stream()
                        .collect(Collectors.toMap(
                                MpesaPayment::getMatchedOrderRef,
                                MpesaPayment::getMpesaCode,
                                (a, b) -> a));

        Map<String, VendorOrder> vendorOrderByRef = all.isEmpty() ? Map.of()
                : vendorOrders.findByOrderRefIn(ids)
                        .stream()
                        .collect(Collectors.toMap(
                                VendorOrder::getOrderRef,
                                v -> v,
                                (a, b) -> a));

        List<AdminOrderRow> open = new ArrayList<>();
        List<AdminOrderRow> closed = new ArrayList<>();
        for (Order o : all) {
            AdminOrderRow row = toRow(o, vendorOrderByRef.get(o.getOrderId()), now,
                    mpesaByOrder.get(o.getOrderId()));
            (isOpen(row) ? open : closed).add(row);
        }
        open.sort(Comparator.comparing(AdminOrderRow::placedAt));              // oldest first
        closed.sort(Comparator.comparing(AdminOrderRow::placedAt).reversed()); // newest first

        List<AdminOrderRow> rows = new ArrayList<>(open);
        rows.addAll(closed);

        OrderTabCounts counts = new OrderTabCounts(
                count(rows, "new"), count(rows, "stuck"), count(rows, "live"),
                count(rows, "completed"), count(rows, "cancelled"), count(rows, "refunded"));

        return new AdminOrdersResponse(summary(all, vendorOrderByRef, now), counts, rows);
    }

    // ----------------------------------------------------------------- dispatch

    /**
     * Approved vendors sorted nearest to the customer first.
     * Vendors with unknown distance come last.
     */
    public List<VendorOption> nearestVendors(String orderId) {
        Order order = findOrder(orderId);
        double[] dest = customerCoords(order);

        return vendors.findAll().stream()
                .filter(v -> v.getVerificationStatus() == VendorProfile.VerificationStatus.APPROVED)
                .map(v -> {
                    double[] at = vendorCoords(v);
                    Double km = (dest != null && at != null)
                            ? round1(haversineKm(dest[0], dest[1], at[0], at[1]))
                            : null;
                    return new VendorOption(v.getId(), v.getBusinessName(), km);
                })
                .sorted(Comparator
                        .comparing(VendorOption::distanceKm, Comparator.nullsLast(Comparator.<Double>naturalOrder()))
                        .thenComparing(VendorOption::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    /** Assigns (or reassigns) an open order to an approved vendor and hands it to the vendor portal. */
    @Transactional
    public void dispatch(String orderId, Long vendorId) {
        if (vendorId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "vendorId is required");
        }
        Order order = findOrder(orderId);
        VendorOrder existing = vendorOrders.findByOrderRef(order.getOrderId()).orElse(null);

        String status = statusName(order);
        if (delivered(order, existing) || isCancelled(status) || "REFUNDED".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is already closed");
        }
        VendorProfile vendor = vendors.findById(vendorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found"));
        if (vendor.getVerificationStatus() != VendorProfile.VerificationStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vendor is not approved");
        }

        order.setVendor(vendor);
        order.setDispatchedAt(LocalDateTime.now(DB_ZONE));
        orders.save(order);

        syncVendorOrder(order, vendor, existing);
    }

    /**
     * Makes the dispatched order visible in the vendor portal.
     * The VendorOrder reuses the customer order's id as its orderRef, so dispatching the
     * same order again (reassign, or re-dispatch after a decline) moves the existing
     * VendorOrder instead of creating a duplicate.
     */
    private void syncVendorOrder(Order order, VendorProfile vendor, VendorOrder existing) {
        VendorOrder vo = existing;

        if (vo == null) {
            vo = new VendorOrder();
            vo.setOrderRef(order.getOrderId());
            vo.setPlacedAt(placed(order, Instant.now()));
            vo.setCustomerName(customerName(order));
            vo.setCustomerPhone(customerPhone(order));

            var address = order.getCustomer() == null ? null : order.getCustomer().getAddress();
            vo.setArea(address != null ? address.getArea() : null);

            for (OrderItem item : order.getItems()) {
                OrderLine line = new OrderLine();
                line.setOrder(vo);
                line.setProductName(item.getProduct().getName());
                line.setQty(item.getQuantity() == null ? 0 : item.getQuantity());
                line.setUnitPrice(item.getPrice() == null ? 0 : item.getPrice().intValue());
                vo.getLines().add(line);
            }
        } else {
            // Reassigned or re-dispatched: reopen it for the new vendor.
            vo.setAcceptedAt(null);
            vo.setRiderName(null);
            vo.setRiderKind(null);
            vo.setDeliveryStage(null);
        }

        vo.setVendor(vendor);
        vo.setStatus(VendorOrder.OrderStatus.NEW);

        double[] dest = customerCoords(order);
        double[] at = vendorCoords(vendor);
        vo.setDistanceKm(dest != null && at != null
                ? round1(haversineKm(dest[0], dest[1], at[0], at[1]))
                : 0);

        vendorOrders.save(vo);
    }

    private Order findOrder(String orderId) {
        return orders.findDetailed(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    // ------------------------------------------------------------- coordinates

    /** Delivery location of the order as {lat, lng}, or null if unknown. */
    private double[] customerCoords(Order o) {
        Customer c = o.getCustomer();
        var a = c == null ? null : c.getAddress();
        if (a != null && a.getLatitude() != null && a.getLongitude() != null) {
            return new double[] { a.getLatitude(), a.getLongitude() };
        }
        return null;
    }

    /** Vendor location as {lat, lng}, or null if unknown. */
    private double[] vendorCoords(VendorProfile v) {
        if (v.getLatitude() != null && v.getLongitude() != null) {
            return new double[] { v.getLatitude(), v.getLongitude() };
        }
        return null;
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    // ------------------------------------------------------------------ summary

    private AdminOrdersSummary summary(List<Order> all, Map<String, VendorOrder> vos, Instant now) {
        LocalDate today = LocalDate.now(ZONE);
        Instant dayStart = today.atStartOfDay(ZONE).toInstant();
        Instant weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(ZONE).toInstant();
        Instant lastWeekStart = weekStart.minus(Duration.ofDays(7));
        Instant end = now.plusSeconds(1);

        long ordersToday = all.stream().filter(o -> !placed(o, now).isBefore(dayStart)).count();
        long cancelledToday = all.stream()
                .filter(o -> isCancelled(statusName(o)) && !placed(o, now).isBefore(dayStart))
                .count();

        Double avgThis = avgMinutes(all, vos, now, weekStart, end);
        Double avgLast = avgMinutes(all, vos, now, lastWeekStart, weekStart);
        Double avgShown = avgThis != null ? avgThis : avgMinutes(all, vos, now, Instant.EPOCH, end);

        return new AdminOrdersSummary(
                cancelledToday,
                ordersToday == 0 ? 0 : round1(100.0 * cancelledToday / ordersToday),
                avgShown == null ? null : Math.round(avgShown),
                (avgThis != null && avgLast != null) ? Math.round(avgThis - avgLast) : null);
    }

    /** Average placed-to-delivered time for orders delivered in [from, to). */
    private Double avgMinutes(List<Order> all, Map<String, VendorOrder> vos, Instant now,
                              Instant from, Instant to) {
        return all.stream()
                .filter(o -> delivered(o, vos.get(o.getOrderId())))
                .filter(o -> {
                    Instant done = deliveredAt(o, vos.get(o.getOrderId()));
                    return done != null && !done.isBefore(from) && done.isBefore(to);
                })
                .mapToLong(o -> Duration.between(placed(o, now), deliveredAt(o, vos.get(o.getOrderId()))).toMinutes())
                .average()
                .stream().boxed().findFirst().orElse(null);
    }

    // --------------------------------------------------------------------- rows

    private AdminOrderRow toRow(Order o, VendorOrder vo, Instant now, String mpesaCode) {
        Instant placed = placed(o, now);
        Classified c = classify(o, vo, placed, now);

        Long durationMinutes = switch (c.tab()) {
            case "completed" -> {
                Instant done = deliveredAt(o, vo);
                yield done == null ? null : Duration.between(placed, done).toMinutes();
            }
            case "new", "stuck", "live" -> Duration.between(placed, now).toMinutes();
            default -> null;
        };

        return new AdminOrderRow(
                o.getOrderId(),
                o.getOrderId(),
                placed,
                customerName(o),
                customerPhone(o),
                customerArea(o),
                itemsSummary(o),
                vendorName(o),
                riderName(vo),
                c.stage(),
                c.tone(),
                c.severe(),
                durationMinutes,
                value(o),
                c.tab(),
                mpesaCode);
    }

    private String customerName(Order o) {
        Customer c = o.getCustomer();
        if (c == null) return "—";
        String full = ((c.getFirstName() == null ? "" : c.getFirstName()) + " "
                     + (c.getLastName() == null ? "" : c.getLastName())).trim();
        if (!full.isEmpty()) return full;
        return notBlank(c.getUserName()) ? c.getUserName() : "—";
    }

    private String customerPhone(Order o) {
        Customer c = o.getCustomer();
        return c == null || c.getPhoneNumber() == null ? "" : c.getPhoneNumber();
    }

    /** Delivery area from the customer's saved address, or null if they haven't set one. */
    private String customerArea(Order o) {
        Customer c = o.getCustomer();
        var a = c == null ? null : c.getAddress();
        return a == null || !notBlank(a.getArea()) ? null : a.getArea();
    }

    /** What was ordered, e.g. "2 × 20L Refill, 1 × 500ml Bottle". */
    private String itemsSummary(Order o) {
        return o.getItems().stream()
                .filter(i -> i.getProduct() != null)
                .map(i -> (i.getQuantity() == null ? 0 : i.getQuantity()) + " × " + i.getProduct().getName())
                .collect(Collectors.joining(", "));
    }

    private String vendorName(Order o) {
        VendorProfile v = o.getVendor();
        return v == null || !notBlank(v.getBusinessName()) ? "—" : v.getBusinessName();
    }

    private String riderName(VendorOrder vo) {
        return vo == null || !notBlank(vo.getRiderName()) ? "—" : vo.getRiderName();
    }

    private long value(Order o) {
        if (o.getTotalAmount() != null) return o.getTotalAmount().longValue();
        return o.getItems().stream()
                .filter(i -> i.getPrice() != null && i.getQuantity() != null)
                .mapToLong(i -> i.getPrice().longValue() * i.getQuantity())
                .sum();
    }

    // ----------------------------------------------------------- classification

    private record Classified(String tab, String stage, String tone, boolean severe) {}

    private Classified classify(Order o, VendorOrder vo, Instant placed, Instant now) {
        String status = statusName(o);
        if ("REFUNDED".equals(status)) return new Classified("refunded", "Refunded", "bad", false);
        if (delivered(o, vo))          return new Classified("completed", "Delivered", "ok", false);
        if (isCancelled(status))       return new Classified("cancelled", "Cancelled", "bad", false);
        return classifyOpen(o, vo, placed, now);
    }

    private Classified classifyOpen(Order o, VendorOrder vo, Instant placed, Instant now) {
        Duration age = Duration.between(placed, now);
        boolean overdue = age.compareTo(STUCK_AFTER) > 0;

        // Not dispatched yet: it belongs on the admin's "New" tab.
        if (o.getVendor() == null) {
            boolean late = age.compareTo(NOT_DISPATCHED_AFTER) > 0;
            return new Classified("new", late ? "Waiting for dispatch" : "New order",
                    late ? "warn" : "ok", late);
        }

        VendorOrder.OrderStatus vs = vo == null ? VendorOrder.OrderStatus.NEW : vo.getStatus();

        // The vendor declined it: back to the admin to dispatch to someone else.
        if (vs == VendorOrder.OrderStatus.CANCELLED) {
            return new Classified("new", "Declined by vendor", "bad", true);
        }

        // Dispatched but the vendor hasn't responded yet.
        if (vs == VendorOrder.OrderStatus.NEW) {
            Instant since = o.getDispatchedAt() != null ? toInstant(o.getDispatchedAt()) : placed;
            if (Duration.between(since, now).compareTo(NOT_ACCEPTED_AFTER) > 0) {
                return new Classified("stuck", "Vendor has not accepted", "warn", overdue);
            }
            if (overdue) return new Classified("stuck", "Open over 2 hrs", "warn", true);
            return new Classified("live", "Dispatched", "ok", false);
        }

        // Accepted and in progress.
        if (overdue) return new Classified("stuck", "Open over 2 hrs", "warn", true);
        return new Classified("live", stageLabel(vs), "ok", false);
    }

    private String stageLabel(VendorOrder.OrderStatus vs) {
        return switch (vs) {
            case PREPARING        -> "Preparing";
            case READY            -> "Ready for pickup";
            case OUT_FOR_DELIVERY -> "Out for delivery";
            default               -> pretty(vs.name());
        };
    }

    /** Delivered according to the customer order itself or the vendor's copy. */
    private boolean delivered(Order o, VendorOrder vo) {
        return isCompleted(statusName(o))
                || (vo != null && vo.getStatus() == VendorOrder.OrderStatus.COMPLETED);
    }

    private Instant deliveredAt(Order o, VendorOrder vo) {
        if (vo != null && vo.getDeliveredAt() != null) return vo.getDeliveredAt();
        return o.getUpdatedAt() == null ? null : toInstant(o.getUpdatedAt());
    }

    private boolean isCompleted(String status) {
        return "COMPLETED".equals(status) || "DELIVERED".equals(status);
    }

    private boolean isCancelled(String status) {
        return "CANCELLED".equals(status) || "CANCELED".equals(status);
    }

    private String pretty(String status) {
        String s = status.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------ helpers

    private String statusName(Order o) {
        return o.getStatus() == null ? "PENDING" : o.getStatus().name();
    }

    private boolean isOpen(AdminOrderRow r) {
        return "new".equals(r.status()) || "stuck".equals(r.status()) || "live".equals(r.status());
    }

    private long count(List<AdminOrderRow> rows, String tab) {
        return rows.stream().filter(r -> tab.equals(r.status())).count();
    }

    private Instant placed(Order o, Instant fallback) {
        return o.getCreatedAt() != null ? toInstant(o.getCreatedAt()) : fallback;
    }

    private Instant toInstant(LocalDateTime t) {
        return t.atZone(DB_ZONE).toInstant();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}