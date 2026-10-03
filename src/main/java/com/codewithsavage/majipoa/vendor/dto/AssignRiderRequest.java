package com.codewithsavage.majipoa.vendor.dto;

import com.codewithsavage.majipoa.shared.model.VendorOrder.RiderKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AssignRiderRequest(
        @NotBlank String   riderName,
        @NotNull  RiderKind riderKind
) {}
