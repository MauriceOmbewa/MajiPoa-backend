package com.codewithsavage.majipoa.vendor.dto;

import com.codewithsavage.majipoa.shared.model.VendorOrder.DeliveryStage;
import com.codewithsavage.majipoa.shared.model.VendorOrder.OrderStatus;
import com.codewithsavage.majipoa.shared.model.VendorOrder.PaymentStatus;
import com.codewithsavage.majipoa.shared.model.VendorOrder.RiderKind;

import java.time.Instant;
import java.util.List;

/** Full order representation used by both the order list and order detail views. */
public record VendorOrderDto(
        Long          id,
        String        orderRef,
        String        customerName,
        String        area,
        double        distanceKm,
        OrderStatus   status,
        PaymentStatus paymentStatus,
        long          total,
        String        placedAgo,
        String        riderName,
        RiderKind     riderKind,
        DeliveryStage deliveryStage,
        Instant       placedAt,
        List<OrderLineDto> lines
) {
    public record OrderLineDto(
            String productName,
            String note,
            int    qty,
            int    unitPrice
    ) {}
}
