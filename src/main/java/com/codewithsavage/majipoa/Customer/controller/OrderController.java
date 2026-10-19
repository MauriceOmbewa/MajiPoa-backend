package com.codewithsavage.majipoa.Customer.controller;

import com.codewithsavage.majipoa.Customer.dto.CustomerResponse;
import com.codewithsavage.majipoa.Customer.dto.OrderResponse;
import com.codewithsavage.majipoa.Customer.dto.UpdateOrderStatusRequest;
import com.codewithsavage.majipoa.Customer.service.CustomerService;
import com.codewithsavage.majipoa.Customer.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;

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

    @GetMapping("/my-orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> getMyOrders(
            Authentication authentication
    ) {
        String googleSub = authentication.getName();

        CustomerResponse profile =
                customerService.getMyProfile(googleSub);

        return ResponseEntity.ok(
                orderService.getCustomerOrders(profile.getCustomerId())
        );
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> getMyOrder(
            @PathVariable String orderId,
            Authentication authentication
    ) {
        String googleSub = authentication.getName();

        CustomerResponse profile =
                customerService.getMyProfile(googleSub);

        return orderService.getCustomerOrder(
                        orderId,
                        profile.getCustomerId()
                )
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<OrderResponse>> getActiveOrders() {
        return ResponseEntity.ok(orderService.getActiveOrders());
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<OrderResponse>> getAllOrders() {

        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable String orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        return orderService.updateOrderStatus(
                        orderId,
                        request.getStatus()
                )
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{orderId}/reorder")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<?> reorder(
            @PathVariable String orderId,
            Authentication authentication
    ) {
        String googleSub = authentication.getName();

        CustomerResponse profile =
                customerService.getMyProfile(googleSub);

        return orderService.reorder(
                        orderId,
                        profile.getCustomerId()
                )
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
