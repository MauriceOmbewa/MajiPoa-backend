package com.codewithsavage.majipoa.rider;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Dev-only demo data that matches the screens (James Kariuki, WM10248, ...).
 * Enable with: majipoa.seed=true   (runs only when there are no riders yet).
 */
@Component
@ConditionalOnProperty(name = "majipoa.seed", havingValue = "true")
public class RiderDataSeeder implements CommandLineRunner {

    private final RiderRepository riders;
    private final DeliveryRepository deliveries;
    private final PayoutRepository payouts;

    public RiderDataSeeder(RiderRepository riders, DeliveryRepository deliveries, PayoutRepository payouts) {
        this.riders = riders;
        this.deliveries = deliveries;
        this.payouts = payouts;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (riders.count() > 0) {
            return;
        }

        Rider james = new Rider();
        james.setFullName("James Kariuki");
        james.setPhone("0798221004");
        james.setEmail("james.kariuki@example.com");
        james.setVehicleType("Motorbike");
        james.setPlateNumber("KMFA 123B");
        james.setCapacity(4);
        james.setMpesaNumber("0798221004");
        james.setVerified(true);
        james.setCreatedAt(LocalDateTime.of(2025, 3, 10, 9, 0));
        james.setStatus(RiderStatus.BUSY);
        james.setOnlineSince(LocalDate.now().atTime(8, 4));
        james.setLatitude(-1.2820);
        james.setLongitude(36.7900);
        james = riders.save(james);

        // ---- completed today (dashboard "Completed today" + history) ----
        LocalDate today = LocalDate.now();
        completed(james, "WM10240", "ABC Water", "Kileleshwa", 3.4, 24, "110", today.atTime(15, 18), 5, true);
        completed(james, "WM10231", "ABC Water", "Kilimani", 1.2, 14, "80", today.atTime(14, 44), 4, true);
        completed(james, "WM10228", "Aqua Fresh", "Hurlingham", 2.1, 19, "90", today.atTime(13, 52), 5, true);
        completed(james, "WM10214", "Blue Spring", "Yaya", 0.9, 11, "80", today.atTime(12, 30), 5, true);

        // ---- earlier this week/month so Earnings and History have rows ----
        int n = 10100;
        for (int back = 1; back <= 4; back++) {
            for (int k = 0; k < 3; k++) {
                completed(james, "WM" + (n++), "Blue Spring", "Westlands", 2.5, 20, "95",
                        today.minusDays(back).atTime(9 + k * 3, 15), 4 + (k % 2), k != 1);
            }
        }

        // ---- the delivery in progress (Active delivery screen) ----
        Delivery active = base(james, "WM10248", "ABC Water", "Kilimani, Ngong Rd",
                "Maurice O.", "Kilimani", 0.8, 2.1, 22, "80");
        active.setPickupLat(-1.2975);
        active.setPickupLng(36.7870);
        active.setCustomerPhone("0712000111");
        active.setDropoffAddress("Argwings Kodhek Rd, Kilimani");
        active.setDropoffUnit("Riverside Court, B12, 3rd floor");
        active.setDropoffLat(-1.2700);
        active.setDropoffLng(36.8040);
        active.setDropoffNotes("Blue gate opposite the mosque, tell security you're for B12.");
        active.setStatus(DeliveryStatus.PICKED_UP);
        LocalDateTime now = LocalDateTime.now();
        active.setOfferedAt(now.minusMinutes(13));
        active.setAcceptedAt(now.minusMinutes(12));
        active.setArrivedAtVendorAt(now.minusMinutes(6));
        active.setPickedUpAt(now.minusMinutes(3));
        item(active, 2, "20L refill - hard jug", "Collect 2 empty jugs", true);
        item(active, 1, "500ml case of 24", "Sealed", true);
        deliveries.save(active);

        // ---- a pending offer (the "New delivery offer" card) ----
        Delivery offer = base(james, "WM10249", "ABC Water", "Kilimani, Ngong Rd",
                "Kevin M.", "Yaya Centre area", 0.8, 2.6, 22, "110");
        offer.setHeavyLoad(true);
        offer.setStatus(DeliveryStatus.OFFERED);
        offer.setOfferedAt(now);
        offer.setOfferExpiresAt(now.plusMinutes(10));
        item(offer, 3, "20L jugs", null, false);
        deliveries.save(offer);

        // ---- past payouts ----
        payout(james, today.minusDays(11), today.minusDays(5), "7410", today.minusDays(4).atTime(18, 0), "SJK4X91LMA");
        payout(james, today.minusDays(18), today.minusDays(12), "6985", today.minusDays(11).atTime(18, 0), "SIH2B77QPD");
    }

    private Delivery base(Rider rider, String order, String vendor, String pickup, String customer,
                          String area, double toPickup, double toDrop, int mins, String fee) {
        Delivery d = new Delivery();
        d.setOrderNumber(order);
        d.setRider(rider);
        d.setVendorName(vendor);
        d.setPickupAddress(pickup);
        d.setCustomerName(customer);
        d.setDropoffAddress(area);
        d.setDropoffArea(area);
        d.setDistanceToPickupKm(toPickup);
        d.setPickupToDropoffKm(toDrop);
        d.setDurationMin(mins);
        d.setFee(new BigDecimal(fee));
        return d;
    }

    private void item(Delivery d, int qty, String name, String note, boolean checked) {
        DeliveryItem i = new DeliveryItem();
        i.setQuantity(qty);
        i.setName(name);
        i.setNote(note);
        i.setChecked(checked);
        d.addItem(i);
    }

    private void completed(Rider rider, String order, String vendor, String area, double km, int mins,
                           String fee, LocalDateTime deliveredAt, int rating, boolean onTime) {
        Delivery d = base(rider, order, vendor, vendor + " depot", "Customer", area, 0.5, km - 0.5, mins, fee);
        d.setStatus(DeliveryStatus.DELIVERED);
        d.setOfferedAt(deliveredAt.minusMinutes(mins + 1));
        d.setAcceptedAt(deliveredAt.minusMinutes(mins));
        d.setArrivedAtVendorAt(deliveredAt.minusMinutes(mins - 4L));
        d.setPickedUpAt(deliveredAt.minusMinutes(mins - 6L));
        d.setArrivedAtCustomerAt(deliveredAt.minusMinutes(1));
        d.setDeliveredAt(deliveredAt);
        d.setOnTime(onTime);
        d.setRating(rating);
        item(d, 2, "20L refill - hard jug", "Collect 2 empty jugs", true);
        deliveries.save(d);
    }

    private void payout(Rider rider, LocalDate from, LocalDate to, String amount, LocalDateTime paidAt, String ref) {
        Payout p = new Payout();
        p.setRider(rider);
        p.setPeriodStart(from);
        p.setPeriodEnd(to);
        p.setAmount(new BigDecimal(amount));
        p.setStatus(PayoutStatus.PAID);
        p.setPaidAt(paidAt);
        p.setMpesaReference(ref);
        payouts.save(p);
    }
}
