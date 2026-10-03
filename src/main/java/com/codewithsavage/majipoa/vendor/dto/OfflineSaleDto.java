package com.codewithsavage.majipoa.vendor.dto;

import java.time.LocalDate;

public record OfflineSaleDto(
        Long      id,
        LocalDate saleDate,
        String    product,
        int       qty,
        int       amount,
        String    channel
) {}
