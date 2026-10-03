package com.codewithsavage.majipoa.vendor.dto;

import jakarta.validation.constraints.Min;

public record UpdatePriceRequest(@Min(0) int price) {}
