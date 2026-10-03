package com.codewithsavage.majipoa.vendor.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank String businessName,
        String waterSource,
        String treatment,
        String phValue,
        String tdsValue,
        String about
) {}
