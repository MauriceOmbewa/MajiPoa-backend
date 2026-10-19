package com.codewithsavage.majipoa.admin.rider;

import com.codewithsavage.majipoa.admin.rider.AdminRiderDtos.*;
import com.codewithsavage.majipoa.rider.Delivery;
import com.codewithsavage.majipoa.rider.DeliveryRepository;
import com.codewithsavage.majipoa.rider.DeliveryStatus;
import com.codewithsavage.majipoa.rider.InvalidDeliveryStateException;
import com.codewithsavage.majipoa.rider.Rider;
import com.codewithsavage.majipoa.rider.RiderNotFoundException;
import com.codewithsavage.majipoa.rider.RiderRepository;
import com.codewithsavage.majipoa.rider.RiderStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Read models and actions behind the admin Riders page.
 *
 * Tab rules:
 *   suspended  -> adminSuspended
 *   applicants -> not verified yet (and not suspended)
 *   online     -> verified, status AVAILABLE or BUSY
 *   offline    -> verified, status OFFLINE
 */
@Service
@Transactional(readOnly = true)
public class AdminRiderService {

    private static final List<DeliveryStatus> ACTIVE = Arrays.stream(DeliveryStatus.values())
            .filter(DeliveryStatus::isActive).toList();

    private final RiderRepository riders;
    private final DeliveryRepository deliveries;

    public AdminRiderService(RiderRepository riders, DeliveryRepository deliveries) {
        this.riders = riders;
        this.deliveries = deliveries;
    }

    public AdminRidersResponse overview() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        List<AdminRiderRow> rows = riders.findAll().stream()
                .map(r -> toRow(r, start, end))
                .toList();

        TabCounts counts = new TabCounts(
                count(rows, "online"), count(rows, "offline"),
                count(rows, "suspended"), count(rows, "applicants"));

        long today = deliveries.countByStatusAndDeliveredAtBetween(DeliveryStatus.DELIVERED, start, end);
        long late = deliveries.countByStatusAndOnTimeFalseAndDeliveredAtBetween(
                DeliveryStatus.DELIVERED, start, end);
        long failed = deliveries.countByStatusAndOfferedAtBetween(DeliveryStatus.CANCELLED, start, end);
        Double avg = deliveries.averageDurationMinutes(DeliveryStatus.DELIVERED, start, end);

        AdminRiderSummary summary = new AdminRiderSummary(
                deliveries.countByStatusIn(ACTIVE),
                today,
                avg == null ? null : Math.round(avg),
                late + failed,
                today == 0 ? 0 : round1(100.0 * (late + failed) / today));

        return new AdminRidersResponse(summary, counts, rows);
    }

    // ------------------------------------------------------------------ actions

    @Transactional
    public AdminRiderRow approve(Long id) {
        Rider r = getOrThrow(id);
        r.setVerified(true);
        return row(riders.save(r));
    }

    @Transactional
    public AdminRiderRow suspend(Long id) {
        Rider r = getOrThrow(id);
        if (deliveries.findFirstByRiderIdAndStatusInOrderByAcceptedAtDesc(id, ACTIVE).isPresent()) {
            throw new InvalidDeliveryStateException(
                    "This rider has an active delivery. Suspend them after it finishes.");
        }
        r.setAdminSuspended(true);
        r.setStatus(RiderStatus.OFFLINE);
        return row(riders.save(r));
    }

    @Transactional
    public AdminRiderRow reinstate(Long id) {
        Rider r = getOrThrow(id);
        r.setAdminSuspended(false);
        return row(riders.save(r));
    }

    // ------------------------------------------------------------------ helpers

    private AdminRiderRow row(Rider r) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        return toRow(r, start, start.plusDays(1));
    }

    private AdminRiderRow toRow(Rider r, LocalDateTime start, LocalDateTime end) {
        String tab = tabOf(r);

        long today = deliveries.countByRiderIdAndStatusAndDeliveredAtBetween(
                r.getId(), DeliveryStatus.DELIVERED, start, end);

        Double avg = deliveries.averageRating(r.getId());

        // "Calculated from the last 100 deliveries", as the page notes say
        List<Delivery> last = deliveries.findTop100ByRiderIdAndStatusOrderByDeliveredAtDesc(
                r.getId(), DeliveryStatus.DELIVERED);
        Integer onTimeRate = last.isEmpty() ? null
                : (int) Math.round(100.0 * last.stream()
                        .filter(d -> Boolean.TRUE.equals(d.getOnTime())).count() / last.size());

        String area = deliveries.findFirstByRiderIdAndDropoffAreaIsNotNullOrderByOfferedAtDesc(r.getId())
                .map(Delivery::getDropoffArea).orElse(null);

        return new AdminRiderRow(
                r.getId(),
                r.getFullName(),
                r.getPhone(),
                area,
                r.getVehicleType(),
                r.getCreatedAt(),
                (int) today,
                avg == null ? null : round1(avg),
                onTimeRate,
                tab,
                "applicants".equals(tab) ? "Awaiting verification" : null);
    }

    private String tabOf(Rider r) {
        if (r.isAdminSuspended()) return "suspended";
        if (!r.isVerified()) return "applicants";
        return r.getStatus() == RiderStatus.OFFLINE ? "offline" : "online";
    }

    private long count(List<AdminRiderRow> rows, String tab) {
        return rows.stream().filter(r -> tab.equals(r.status())).count();
    }

    private Rider getOrThrow(Long id) {
        return riders.findById(id).orElseThrow(() -> new RiderNotFoundException(id));
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}