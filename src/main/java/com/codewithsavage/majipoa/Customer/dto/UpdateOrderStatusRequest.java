package com.codewithsavage.majipoa.Customer.dto;

import com.codewithsavage.majipoa.Customer.model.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateOrderStatusRequest {

    @NotNull
    private OrderStatus status;
}