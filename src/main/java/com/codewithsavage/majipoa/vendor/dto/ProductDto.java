package com.codewithsavage.majipoa.vendor.dto;

import com.codewithsavage.majipoa.shared.model.Product.ProductStatus;

public record ProductDto(
        Long          id,
        String        name,
        String        note,
        String        size,
        int           price,
        int           marketMin,
        int           marketMax,
        int           availableToday,
        int           soldToday,
        ProductStatus status
) {}
