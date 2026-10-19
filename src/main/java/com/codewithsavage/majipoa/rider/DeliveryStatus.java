package com.codewithsavage.majipoa.rider;

/**
 * Life of a delivery, matching the "Progress" timeline on the Active delivery screen:
 * ACCEPTED -> AT_VENDOR -> PICKED_UP (on the way to customer) -> ARRIVED -> DELIVERED
 */
public enum DeliveryStatus {
    OFFERED,
    ACCEPTED,
    AT_VENDOR,
    PICKED_UP,
    ARRIVED,
    DELIVERED,
    DECLINED,
    EXPIRED,
    CANCELLED;

    public boolean isActive() {
        return this == ACCEPTED || this == AT_VENDOR || this == PICKED_UP || this == ARRIVED;
    }

    /** The only status a delivery may move to next, or null when there is none. */
    public DeliveryStatus next() {
        return switch (this) {
            case ACCEPTED -> AT_VENDOR;
            case AT_VENDOR -> PICKED_UP;
            case PICKED_UP -> ARRIVED;
            case ARRIVED -> DELIVERED;
            default -> null;
        };
    }
}
