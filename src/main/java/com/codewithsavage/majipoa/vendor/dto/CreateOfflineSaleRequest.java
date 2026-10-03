package com.codewithsavage.majipoa.vendor.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateOfflineSaleRequest(
        @NotNull  LocalDate saleDate,
        @NotBlank String    product,
        @Min(1)   int       qty,
        @Min(1)   int       amount,
        @NotBlank String    channel
) {}
