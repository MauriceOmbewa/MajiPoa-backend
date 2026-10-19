package com.codewithsavage.majipoa.admin;

import com.codewithsavage.majipoa.rider.*;
import com.codewithsavage.majipoa.shared.model.*;
import com.codewithsavage.majipoa.shared.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Seeds demo data for the admin portal on first startup.
 * Idempotent: checks for existing admin user before running.
 */
@Component
@RequiredArgsConstructor
public class AdminDataSeeder implements ApplicationListener<ApplicationReadyEvent> {

    private final UserRepository userRepo;
    private final VendorProfileRepository vendorProfileRepo;
    private final VendorOrderRepository vendorOrderRepo;
    private final CustomerProfileRepository customerProfileRepo;
    private final MpesaPaymentRepository mpesaPaymentRepo;
    private final RefundCaseRepository refundCaseRepo;
    private final PayoutRecordRepository payoutRecordRepo;
    private final RiderRepository riderRepo;
    private final DeliveryRepository deliveryRepo;
    private final PayoutRepository payoutRepo;

    @Override
    @Transactional
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // Idempotent guard — only seed if no admin exists yet
        boolean adminExists = userRepo.findAll().stream()
                .anyMatch(u -> u.getRole() == Role.ADMIN);
        if (adminExists) {
            return;
        }

        // ── 1. Admin user ─────────────────────────────────────────────────────
        AppUser admin = new AppUser();
        admin.setGoogleSub("admin-google-sub-000001");
        admin.setEmail("admin@majipoa.co.ke");
        admin.setName("MajiPoa Admin");
        admin.setRole(Role.ADMIN);
        userRepo.save(admin);

        // ── 2. Vendor users + VendorProfiles ──────────────────────────────────
        AppUser vendorUser1 = makeUser("vendor-sub-001", "vendor1@example.com", "Alice Wambui", Role.VENDOR);
        AppUser vendorUser2 = makeUser("vendor-sub-002", "vendor2@example.com", "Brian Otieno", Role.VENDOR);
        AppUser vendorUser3 = makeUser("vendor-sub-003", "vendor3@example.com", "Cynthia Mwangi", Role.VENDOR);

        VendorProfile vp1 = makeVendorProfile(vendorUser1, "ABC Water Works",
                -1.2921, 36.8219, VendorProfile.VerificationStatus.APPROVED, VendorProfile.SubscriptionPlan.PRO);
        VendorProfile vp2 = makeVendorProfile(vendorUser2, "Blue Spring Kenya",
                -1.3000, 36.7800, VendorProfile.VerificationStatus.PENDING, VendorProfile.SubscriptionPlan.FREE);
        VendorProfile vp3 = makeVendorProfile(vendorUser3, "Aqua Fresh Ltd",
                -1.2700, 36.8040, VendorProfile.VerificationStatus.APPROVED, VendorProfile.SubscriptionPlan.PRO);

        // ── 3. Customer profiles ──────────────────────────────────────────────
        makeCustomer(1001L, "Maurice Onyango", "0712345001", "Kilimani", 12, 18500, -2);
        makeCustomer(1002L, "Priya Sharma", "0712345002", "Westlands", 5, 7500, -1);
        makeCustomer(1003L, "Kevin Mutua", "0712345003", "Yaya Centre area", 22, 34000, 0);
        makeCustomer(1004L, "Grace Njeri", "0712345004", "Hurlingham", 1, 1200, -7);
        makeCustomer(1005L, "David Kamau", "0712345005", "Kileleshwa", 0, 0, -30);

        // ── 4. VendorOrders ───────────────────────────────────────────────────
        makeOrder("#ADM10001", vp1, "Maurice Onyango", "0712345001", "Kilimani",
                VendorOrder.OrderStatus.COMPLETED, VendorOrder.PaymentStatus.PAID,
                Instant.now().minus(2, ChronoUnit.HOURS), "James Kariuki");
        makeOrder("#ADM10002", vp1, "Priya Sharma", "0712345002", "Westlands",
                VendorOrder.OrderStatus.PREPARING, VendorOrder.PaymentStatus.PAID,
                Instant.now().minus(45, ChronoUnit.MINUTES), null);
        makeOrder("#ADM10003", vp2, "Kevin Mutua", "0712345003", "Yaya Centre area",
                VendorOrder.OrderStatus.NEW, VendorOrder.PaymentStatus.PAID,
                Instant.now().minus(35, ChronoUnit.MINUTES), null);
        makeOrder("#ADM10004", vp3, "Grace Njeri", "0712345004", "Hurlingham",
                VendorOrder.OrderStatus.OUT_FOR_DELIVERY, VendorOrder.PaymentStatus.PAID,
                Instant.now().minus(1, ChronoUnit.HOURS), "James Kariuki");
        makeOrder("#ADM10005", vp1, "David Kamau", "0712345005", "Kileleshwa",
                VendorOrder.OrderStatus.CANCELLED, VendorOrder.PaymentStatus.REFUNDED,
                Instant.now().minus(3, ChronoUnit.HOURS), null);
        makeOrder("#ADM10006", vp2, "Maurice Onyango", "0712345001", "Kilimani",
                VendorOrder.OrderStatus.NEW, VendorOrder.PaymentStatus.PAID,
                Instant.now().minus(40, ChronoUnit.MINUTES), null);
        makeOrder("#ADM10007", vp3, "Kevin Mutua", "0712345003", "Yaya Centre area",
                VendorOrder.OrderStatus.COMPLETED, VendorOrder.PaymentStatus.PAID,
                Instant.now().minus(4, ChronoUnit.HOURS), "James Kariuki");
        makeOrder("#ADM10008", vp1, "Priya Sharma", "0712345002", "Westlands",
                VendorOrder.OrderStatus.PREPARING, VendorOrder.PaymentStatus.PAID,
                Instant.now().minus(50, ChronoUnit.MINUTES), null);
        makeOrder("#ADM10009", vp2, "Grace Njeri", "0712345004", "Hurlingham",
                VendorOrder.OrderStatus.NEW, VendorOrder.PaymentStatus.INVOICED,
                Instant.now().minus(10, ChronoUnit.MINUTES), null);
        makeOrder("#ADM10010", vp3, "David Kamau", "0712345005", "Kileleshwa",
                VendorOrder.OrderStatus.COMPLETED, VendorOrder.PaymentStatus.PAID,
                Instant.now().minus(5, ChronoUnit.HOURS), "James Kariuki");

        // ── 5. Riders ─────────────────────────────────────────────────────────
        Rider r1 = makeRider("James Kariuki", "0798221004", "james2@example.com",
                "Motorbike", "KCA 001A", true, RiderStatus.AVAILABLE,
                -1.2921, 36.8219);
        Rider r2 = makeRider("Faith Achieng", "0798221005", "faith@example.com",
                "Bicycle", "KCB 002B", true, RiderStatus.AVAILABLE,
                -1.2950, 36.8250);
        Rider r3 = makeRider("Tony Njoroge", "0798221006", "tony@example.com",
                "Motorbike", "KCC 003C", false, RiderStatus.OFFLINE,
                -1.3100, 36.7900);

        // ── 6. Deliveries ─────────────────────────────────────────────────────
        makeDelivery(r1, "ADM10001", "ABC Water Works", "Kilimani",
                DeliveryStatus.DELIVERED, LocalDateTime.now().minusHours(2));
        makeDelivery(r1, "ADM10004", "Aqua Fresh Ltd", "Hurlingham",
                DeliveryStatus.PICKED_UP, null);
        makeDelivery(r2, "ADM10007", "Aqua Fresh Ltd", "Yaya Centre area",
                DeliveryStatus.DELIVERED, LocalDateTime.now().minusHours(4));
        makeDelivery(r1, "ADM10010", "Aqua Fresh Ltd", "Kileleshwa",
                DeliveryStatus.DELIVERED, LocalDateTime.now().minusHours(5));
        makeDelivery(r2, "ADM10001B", "Blue Spring Kenya", "Westlands",
                DeliveryStatus.DELIVERED, LocalDateTime.now().minusHours(6));

        // ── 7. Mpesa Payments ─────────────────────────────────────────────────
        makeMpesaPayment("QHG1234567", "0712345001", 1200,
                "#ADM10001", MpesaPayment.PaymentStatus.MATCHED);
        makeMpesaPayment("QHG7654321", "0712345003", 2500,
                "#ADM10007", MpesaPayment.PaymentStatus.MATCHED);
        makeMpesaPayment("QHG9999999", "0799000000", 800,
                null, MpesaPayment.PaymentStatus.UNMATCHED);

        // ── 8. Refund cases ───────────────────────────────────────────────────
        makeRefundCase("#ADM10005", "David Kamau", "ABC Water Works",
                1200, "Cancelled order", "Customer cancelled after 3 hours, vendor did not refund.",
                RefundCase.RefundStatus.OPEN, null);
        makeRefundCase("#ADM10001", "Maurice Onyango", "ABC Water Works",
                500, "Wrong item", "Customer received wrong size jug.",
                RefundCase.RefundStatus.APPROVED, "QREF123456");

        // ── 9. PayoutRecord (vendor) ───────────────────────────────────────────
        PayoutRecord pr = new PayoutRecord();
        pr.setVendor(vp1);
        pr.setPaidOn(LocalDate.now().minusDays(4));
        pr.setPeriodStart(LocalDate.now().minusDays(11));
        pr.setPeriodEnd(LocalDate.now().minusDays(5));
        pr.setOrders(22);
        pr.setGross(44000);
        pr.setDeductions(3520);
        pr.setPaid(40480);
        pr.setReference("SJV1234ABC");
        payoutRecordRepo.save(pr);

        // ── 10. Rider Payout ──────────────────────────────────────────────────
        Payout riderPayout = new Payout();
        riderPayout.setRider(r1);
        riderPayout.setPeriodStart(LocalDate.now().minusDays(7));
        riderPayout.setPeriodEnd(LocalDate.now().minusDays(1));
        riderPayout.setAmount(new BigDecimal("4200"));
        riderPayout.setStatus(PayoutStatus.PENDING);
        payoutRepo.save(riderPayout);
    }

    // ─── Helper methods ───────────────────────────────────────────────────────

    private AppUser makeUser(String sub, String email, String name, Role role) {
        AppUser u = new AppUser();
        u.setGoogleSub(sub);
        u.setEmail(email);
        u.setName(name);
        u.setRole(role);
        return userRepo.save(u);
    }

    private VendorProfile makeVendorProfile(AppUser user, String businessName,
                                             double lat, double lng,
                                             VendorProfile.VerificationStatus verStatus,
                                             VendorProfile.SubscriptionPlan plan) {
        VendorProfile vp = new VendorProfile();
        vp.setUser(user);
        vp.setBusinessName(businessName);
        vp.setLatitude(lat);
        vp.setLongitude(lng);
        vp.setVerificationStatus(verStatus);
        vp.setPlan(plan);
        return vendorProfileRepo.save(vp);
    }

    private void makeCustomer(Long userId, String name, String phone, String area,
                               int totalOrders, long totalSpend, int daysAgo) {
        CustomerProfile cp = new CustomerProfile();
        cp.setUserId(userId);
        cp.setName(name);
        cp.setPhone(phone);
        cp.setArea(area);
        cp.setTotalOrders(totalOrders);
        cp.setTotalSpend(totalSpend);
        if (daysAgo <= 0) {
            cp.setLastOrderAt(Instant.now().plus(daysAgo, ChronoUnit.DAYS));
        }
        cp.setStatus(totalOrders == 0 ? CustomerProfile.CustomerStatus.AT_RISK
                : CustomerProfile.CustomerStatus.ACTIVE);
        customerProfileRepo.save(cp);
    }

    private void makeOrder(String orderRef, VendorProfile vendor,
                            String customerName, String customerPhone, String area,
                            VendorOrder.OrderStatus status, VendorOrder.PaymentStatus paymentStatus,
                            Instant placedAt, String riderName) {
        VendorOrder o = new VendorOrder();
        o.setOrderRef(orderRef);
        o.setVendor(vendor);
        o.setCustomerName(customerName);
        o.setCustomerPhone(customerPhone);
        o.setArea(area);
        o.setStatus(status);
        o.setPaymentStatus(paymentStatus);
        o.setPlacedAt(placedAt);
        o.setRiderName(riderName);
        if (status == VendorOrder.OrderStatus.COMPLETED) {
            o.setDeliveredAt(placedAt.plus(30, ChronoUnit.MINUTES));
        }
        if (status != VendorOrder.OrderStatus.NEW) {
            o.setAcceptedAt(placedAt.plus(5, ChronoUnit.MINUTES));
        }

        // Add an order line
        OrderLine line = new OrderLine();
        line.setOrder(o);
        line.setProductName("20L Refill - Hard Jug");
        line.setQty(2);
        line.setUnitPrice(300);
        o.getLines().add(line);

        vendorOrderRepo.save(o);
    }

    private Rider makeRider(String fullName, String phone, String email,
                             String vehicleType, String plate, boolean verified,
                             RiderStatus status, double lat, double lng) {
        Rider r = new Rider();
        r.setFullName(fullName);
        r.setPhone(phone);
        r.setEmail(email);
        r.setVehicleType(vehicleType);
        r.setPlateNumber(plate);
        r.setCapacity(4);
        r.setMpesaNumber(phone);
        r.setVerified(verified);
        r.setStatus(status);
        r.setLatitude(lat);
        r.setLongitude(lng);
        return riderRepo.save(r);
    }

    private void makeDelivery(Rider rider, String orderNumber, String vendorName, String area,
                               DeliveryStatus status, LocalDateTime deliveredAt) {
        Delivery d = new Delivery();
        d.setRider(rider);
        d.setOrderNumber(orderNumber);
        d.setVendorName(vendorName);
        d.setDropoffArea(area);
        d.setPickupAddress(vendorName + " depot");
        d.setDropoffAddress(area);
        d.setDistanceToPickupKm(0.5);
        d.setPickupToDropoffKm(2.0);
        d.setDurationMin(20);
        d.setFee(new BigDecimal("110"));
        d.setStatus(status);
        d.setOfferedAt(LocalDateTime.now().minusHours(6));
        d.setAcceptedAt(LocalDateTime.now().minusHours(5));
        if (deliveredAt != null) {
            d.setDeliveredAt(deliveredAt);
            d.setOnTime(true);
            d.setRating(4);
        }
        deliveryRepo.save(d);
    }

    private void makeMpesaPayment(String code, String phone, long amount,
                                   String orderRef, MpesaPayment.PaymentStatus status) {
        MpesaPayment p = new MpesaPayment();
        p.setMpesaCode(code);
        p.setPhone(phone);
        p.setAmount(amount);
        p.setMatchedOrderRef(orderRef);
        p.setStatus(status);
        mpesaPaymentRepo.save(p);
    }

    private void makeRefundCase(String orderId, String customerName, String vendorName,
                                 long amount, String tag, String description,
                                 RefundCase.RefundStatus status, String mpesaRef) {
        RefundCase rc = new RefundCase();
        rc.setOrderId(orderId);
        rc.setCustomerName(customerName);
        rc.setVendorName(vendorName);
        rc.setAmount(amount);
        rc.setTag(tag);
        rc.setDescription(description);
        rc.setStatus(status);
        rc.setMpesaRef(mpesaRef);
        if (status == RefundCase.RefundStatus.APPROVED) {
            rc.setResolvedAt(Instant.now().minus(1, ChronoUnit.DAYS));
            rc.setResolvedBy("admin");
            rc.setBornBy("platform");
        }
        refundCaseRepo.save(rc);
    }
}
