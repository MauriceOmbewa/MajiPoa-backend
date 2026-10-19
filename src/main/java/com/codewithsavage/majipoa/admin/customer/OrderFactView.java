package com.codewithsavage.majipoa.admin.customer;

import com.codewithsavage.majipoa.Customer.model.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface OrderFactView {
    String getCustomerId();
    BigDecimal getAmount();
    LocalDateTime getPlacedAt();
    OrderStatus getStatus();
}