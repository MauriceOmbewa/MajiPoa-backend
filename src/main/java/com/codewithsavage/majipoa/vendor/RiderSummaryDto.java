package com.codewithsavage.majipoa.vendor;

public record RiderSummaryDto(
        Long id,
        String fullName,
        String phone,
        double distanceKm
) {}
