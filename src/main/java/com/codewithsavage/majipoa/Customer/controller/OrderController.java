package com.codewithsavage.majipoa.Customer.controller;

import com.codewithsavage.majipoa.Customer.dto.CustomerResponse;
import com.codewithsavage.majipoa.Customer.dto.OrderResponse;
import com.codewithsavage.majipoa.Customer.service.CustomerService;
import com.codewithsavage.majipoa.Customer.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final CustomerService customerService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> createOrder(
            Authentication authentication) {

        String googleSub = authentication.getName();

        CustomerResponse profile =
                customerService.getMyProfile(googleSub);

        String customerId = profile.getCustomerId();

        return orderService.createOrder(customerId)
                .map(order ->
                        ResponseEntity.status(HttpStatus.CREATED)
                                .body(order))
                .orElseGet(() ->
                        ResponseEntity.badRequest().build());
    }
}
