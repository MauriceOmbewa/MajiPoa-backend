package com.codewithsavage.majipoa.rider;

import com.codewithsavage.majipoa.rider.RiderDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Everything that happens to a delivery: offering it, accept/decline, the pickup-to-drop-off
 * steps, item checking, rating, and the mapping to the shapes the rider screens use.
 */
@Service
@Transactional
public class DeliveryService {

    private static final double AVG_SPEED_KMH = 16.0;
    private static final int DEFAULT_OFFER_SECONDS = 30;
    private static final List<DeliveryStatus> ACTIVE = List.of(
            DeliveryStatus.ACCEPTED, DeliveryStatus.AT_VENDOR,
            DeliveryStatus.PICKED_UP, DeliveryStatus.ARRIVED);

    private final DeliveryRepository deliveries;
    private final RiderRepository riders;

    public DeliveryService(DeliveryRepository deliveries, RiderRepository riders) {
        this.deliveries = deliveries;
        this.riders = riders;
    }

    // ------------------------------------------------------------------ offers

    /** Entry point for the order/dispatch side: offers a delivery to one rider. */
    public OfferDto createOffer(CreateOfferRequest req) {
        expireOverdue();   // first, because the bulk update clears the persistence context
        Rider rider = riders.findById(req.riderId())
                .orElseThrow(() -> new RiderNotFoundException(req.riderId()));
        if (rider.getStatus() == RiderStatus.OFFLINE) {
            throw new InvalidDeliveryStateException("Rider is offline and cannot receive offers");
        }
        if (findPendingOffer(rider.getId()).isPresent()) {
            throw new InvalidDeliveryStateException("Rider already has a pending offer");
        }

        LocalDateTime now = LocalDateTime.now();
        Delivery d = new Delivery();
        d.setOrderNumber("TMP-" + java.util.UUID.randomUUID());   // replaced once the id is known
        d.setRider(rider);
        d.setVendorName(req.vendorName());
        d.setPickupAddress(req.pickupAddress());
        d.setPickupLat(req.pickupLat());
        d.setPickupLng(req.pickupLng());
        d.setCustomerName(req.customerName());
        d.setCustomerPhone(req.customerPhone());
        d.setDropoffAddress(req.dropoffAddress());
        d.setDropoffUnit(req.dropoffUnit());
        d.setDropoffArea(req.dropoffArea());
        d.setDropoffLat(req.dropoffLat());
        d.setDropoffLng(req.dropoffLng());
        d.setDropoffNotes(req.dropoffNotes());
        d.setDistanceToPickupKm(req.distanceToPickupKm());
        d.setPickupToDropoffKm(req.pickupToDropoffKm());
        d.setDurationMin(req.durationMin());
        d.setFee(req.fee());
        d.setPaid(req.paid());
        d.setHeavyLoad(req.heavyLoad());
        d.setStatus(DeliveryStatus.OFFERED);
        d.setOfferedAt(now);
        int seconds = req.offerSeconds() == null ? DEFAULT_OFFER_SECONDS : req.offerSeconds();
        d.setOfferExpiresAt(now.plusSeconds(seconds));
        for (ItemRequest it : req.items()) {
            DeliveryItem item = new DeliveryItem();
            item.setQuantity(it.quantity());
            item.setName(it.name());
            item.setNote(it.note());
            d.addItem(item);
        }

        Delivery saved = deliveries.save(d);
        saved.setOrderNumber("WM" + (10000 + saved.getId()));
        return toOfferDto(saved);
    }

    public Optional<OfferDto> currentOffer(Long riderId) {
        expireOverdue();
        return findPendingOffer(riderId).map(this::toOfferDto);
    }

    public ActiveDeliveryDto accept(Long riderId, Long deliveryId) {
        Delivery d = getOwned(riderId, deliveryId);
        if (d.getStatus() != DeliveryStatus.OFFERED) {
            throw new InvalidDeliveryStateException("This offer is no longer available");
        }
        LocalDateTime now = LocalDateTime.now();
        if (d.getOfferExpiresAt() != null && d.getOfferExpiresAt().isBefore(now)) {
            throw new InvalidDeliveryStateException("This offer has expired");
        }
        Rider rider = d.getRider();
        if (rider.getStatus() == RiderStatus.OFFLINE) {
            throw new InvalidDeliveryStateException("Go online to accept deliveries");
        }
        if (findActive(riderId).isPresent()) {
            throw new InvalidDeliveryStateException("Finish your current delivery before accepting another");
        }
        d.setStatus(DeliveryStatus.ACCEPTED);
        d.setAcceptedAt(now);
        rider.setStatus(RiderStatus.BUSY);
        return toActiveDto(d);
    }

    public void decline(Long riderId, Long deliveryId) {
        Delivery d = getOwned(riderId, deliveryId);
        if (d.getStatus() != DeliveryStatus.OFFERED) {
            throw new InvalidDeliveryStateException("This offer is no longer available");
        }
        d.setStatus(DeliveryStatus.DECLINED);
    }

    // ------------------------------------------------------------ active delivery

    public Optional<Delivery> findActive(Long riderId) {
        return deliveries.findFirstByRiderIdAndStatusInOrderByAcceptedAtDesc(riderId, ACTIVE);
    }

    /** Moves the delivery one step forward; the target must be exactly the next status. */
    public ActiveDeliveryDto advance(Long riderId, Long deliveryId, DeliveryStatus target) {
        Delivery d = getOwned(riderId, deliveryId);
        DeliveryStatus expected = d.getStatus().next();
        if (expected == null || expected != target) {
            throw new InvalidDeliveryStateException(
                    "Cannot move delivery from " + d.getStatus() + " to " + target);
        }
        LocalDateTime now = LocalDateTime.now();
        switch (target) {
            case AT_VENDOR -> d.setArrivedAtVendorAt(now);
            case PICKED_UP -> {
                boolean allChecked = d.getItems().stream().allMatch(DeliveryItem::isChecked);
                if (!allChecked) {
                    throw new InvalidDeliveryStateException("Check every item before confirming pickup");
                }
                d.setPickedUpAt(now);
            }
            case ARRIVED -> d.setArrivedAtCustomerAt(now);
            case DELIVERED -> {
                d.setDeliveredAt(now);
                // On time = whole job (accept -> handover) finished within the estimated duration.
                long minutes = Duration.between(d.getAcceptedAt(), now).toMinutes();
                d.setOnTime(minutes <= d.getDurationMin());
                d.getRider().setStatus(RiderStatus.AVAILABLE);
            }
            default -> throw new InvalidDeliveryStateException("Unsupported status " + target);
        }
        d.setStatus(target);
        return toActiveDto(d);
    }

    /** Ticks or unticks one item during the pickup check. */
    public ActiveDeliveryDto checkItem(Long riderId, Long deliveryId, Long itemId, boolean checked) {
        Delivery d = getOwned(riderId, deliveryId);
        if (d.getStatus() != DeliveryStatus.ACCEPTED && d.getStatus() != DeliveryStatus.AT_VENDOR) {
            throw new InvalidDeliveryStateException("Items can only be checked before pickup");
        }
        DeliveryItem item = d.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new DeliveryNotFoundException("Item " + itemId + " not found on this delivery"));
        item.setChecked(checked);
        return toActiveDto(d);
    }

    /** Customer-side call: rate a finished delivery (1-5). */
    public void rate(Long deliveryId, int rating) {
        Delivery d = deliveries.findById(deliveryId).orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
        if (d.getStatus() != DeliveryStatus.DELIVERED) {
            throw new InvalidDeliveryStateException("Only delivered orders can be rated");
        }
        d.setRating(rating);
    }

    // ------------------------------------------------------------------ helpers

    private Delivery getOwned(Long riderId, Long deliveryId) {
        return deliveries.findByIdAndRiderId(deliveryId, riderId)
                .orElseThrow(() -> new DeliveryNotFoundException(deliveryId));
    }

    private Optional<Delivery> findPendingOffer(Long riderId) {
        return deliveries.findFirstByRiderIdAndStatusAndOfferExpiresAtAfterOrderByOfferedAtDesc(
                riderId, DeliveryStatus.OFFERED, LocalDateTime.now());
    }

    private void expireOverdue() {
        deliveries.expireOverdue(DeliveryStatus.EXPIRED, DeliveryStatus.OFFERED, LocalDateTime.now());
    }

    // ------------------------------------------------------------------ mapping

    public OfferDto toOfferDto(Delivery d) {
        long secondsLeft = Math.max(0, Duration.between(LocalDateTime.now(), d.getOfferExpiresAt()).getSeconds());
        return new OfferDto(
                d.getId(),
                d.getOrderNumber(),
                d.getFee(),
                round1(d.getDistanceToPickupKm() + d.getPickupToDropoffKm()),
                d.getDurationMin(),
                d.isHeavyLoad(),
                toItemDtos(d),
                d.getVendorName(),
                d.getPickupAddress(),
                d.getDistanceToPickupKm(),
                d.getCustomerName(),
                d.getDropoffArea(),
                d.getPickupToDropoffKm(),
                d.getOfferExpiresAt(),
                secondsLeft);
    }

    public ActiveDeliveryDto toActiveDto(Delivery d) {
        Rider r = d.getRider();
        DeliveryStatus s = d.getStatus();
        boolean toVendor = s == DeliveryStatus.ACCEPTED || s == DeliveryStatus.AT_VENDOR;

        Double targetLat = toVendor ? d.getPickupLat() : d.getDropoffLat();
        Double targetLng = toVendor ? d.getPickupLng() : d.getDropoffLng();
        double remaining;
        if (r.getLatitude() != null && r.getLongitude() != null && targetLat != null && targetLng != null) {
            remaining = haversineKm(r.getLatitude(), r.getLongitude(), targetLat, targetLng);
        } else {
            remaining = toVendor ? d.getDistanceToPickupKm() : d.getPickupToDropoffKm();
        }
        remaining = round1(remaining);
        int eta = (int) Math.ceil(remaining / AVG_SPEED_KMH * 60.0);

        String destinationLabel = toVendor
                ? d.getVendorName()
                : (d.getDropoffUnit() != null && !d.getDropoffUnit().isBlank()
                        ? d.getDropoffUnit().split(",")[0].trim()
                        : d.getCustomerName());

        // The customer's number stays available only until the delivery is confirmed.
        String phone = s == DeliveryStatus.DELIVERED ? null : d.getCustomerPhone();

        return new ActiveDeliveryDto(
                d.getId(),
                d.getOrderNumber(),
                s,
                statusLabel(s),
                d.getVendorName(),
                d.getPickupAddress(),
                d.getPickupLat(),
                d.getPickupLng(),
                d.getCustomerName(),
                phone,
                d.getDropoffAddress(),
                d.getDropoffUnit(),
                d.getDropoffArea(),
                d.getDropoffNotes(),
                d.getDropoffLat(),
                d.getDropoffLng(),
                r.getLatitude(),
                r.getLongitude(),
                destinationLabel,
                toVendor ? d.getPickupAddress() : d.getDropoffAddress(),
                remaining,
                eta,
                d.getFee(),
                d.isPaid(),
                toItemDtos(d),
                buildTimeline(d),
                s.next(),
                nextActionLabel(s));
    }

    public CompletedDto toCompletedDto(Delivery d) {
        int minutes = (d.getAcceptedAt() != null && d.getDeliveredAt() != null)
                ? (int) Duration.between(d.getAcceptedAt(), d.getDeliveredAt()).toMinutes()
                : d.getDurationMin();
        return new CompletedDto(
                d.getId(),
                d.getDeliveredAt(),
                d.getOrderNumber(),
                d.getVendorName(),
                d.getDropoffArea(),
                round1(d.getDistanceToPickupKm() + d.getPickupToDropoffKm()),
                minutes,
                d.getFee(),
                d.getRating());
    }

    private List<ItemDto> toItemDtos(Delivery d) {
        return d.getItems().stream()
                .map(i -> new ItemDto(i.getId(), i.getQuantity(), i.getName(), i.getNote(), i.isChecked()))
                .toList();
    }

    private List<TimelineStepDto> buildTimeline(Delivery d) {
        int done = switch (d.getStatus()) {
            case ACCEPTED -> 1;
            case AT_VENDOR -> 2;
            case PICKED_UP -> 3;
            case ARRIVED -> 4;
            case DELIVERED -> 5;
            default -> 0;
        };
        long checked = d.getItems().stream().filter(DeliveryItem::isChecked).count();

        String[] keys = {"accepted", "at_vendor", "picked_up", "on_the_way", "delivered"};
        String[] labels = {"Accepted", "Arrived at " + d.getVendorName(), "Picked up",
                "On the way to customer", "Delivered"};
        LocalDateTime[] times = {d.getAcceptedAt(), d.getArrivedAtVendorAt(), d.getPickedUpAt(),
                d.getArrivedAtCustomerAt(), d.getDeliveredAt()};
        String[] hints = {null, "Tap below when you reach the vendor",
                "Check every item, then confirm pickup", "Tap below when you arrive",
                "Confirm once the customer has received the order"};

        List<TimelineStepDto> steps = new ArrayList<>();
        for (int i = 0; i < keys.length; i++) {
            int n = i + 1;
            String state = n <= done ? "done" : (n == done + 1 ? "current" : "pending");
            String detail = null;
            if (n == 3 && "done".equals(state)) {
                detail = checked + " items checked";
            } else if ("current".equals(state)) {
                detail = hints[i];
            }
            steps.add(new TimelineStepDto(keys[i], labels[i], times[i], detail, state));
        }
        return steps;
    }

    private String statusLabel(DeliveryStatus s) {
        return switch (s) {
            case OFFERED -> "New offer";
            case ACCEPTED -> "Heading to vendor";
            case AT_VENDOR -> "At vendor";
            case PICKED_UP -> "On the way to customer";
            case ARRIVED -> "Arrived at customer";
            case DELIVERED -> "Delivered";
            case DECLINED -> "Declined";
            case EXPIRED -> "Expired";
            case CANCELLED -> "Cancelled";
        };
    }

    private String nextActionLabel(DeliveryStatus s) {
        return switch (s) {
            case ACCEPTED -> "I've arrived at the vendor";
            case AT_VENDOR -> "Confirm pickup";
            case PICKED_UP -> "I've arrived";
            case ARRIVED -> "Confirm delivery";
            default -> null;
        };
    }

    static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
