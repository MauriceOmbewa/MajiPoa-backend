package com.codewithsavage.majipoa.Customer.controller;

import com.codewithsavage.majipoa.Customer.dto.CartItemRequest;
import com.codewithsavage.majipoa.Customer.dto.CartResponse;
import com.codewithsavage.majipoa.Customer.dto.CustomerResponse;
import com.codewithsavage.majipoa.Customer.service.CartItemService;
import com.codewithsavage.majipoa.Customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartItemController {

    private final CartItemService cartItemService;
    private final CustomerService customerService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<String> addToCart(
            Authentication authentication,
            @RequestBody CartItemRequest request) {

        String googleSub = authentication.getName();

        CustomerResponse profile =
                customerService.getMyProfile(googleSub);

        String customerId = profile.getCustomerId();

        if (!cartItemService.addToCart(customerId, request)) {
            return ResponseEntity.badRequest()
                    .body("Product Out of Stock or User not found or Product not found");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body("Product added to cart successfully");
    }

    @DeleteMapping("/items/{productId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> removeFromCart(
            Authentication authentication,
            @PathVariable String productId) {

        String googleSub = authentication.getName();

        CustomerResponse profile =
                customerService.getMyProfile(googleSub);

        String customerId = profile.getCustomerId();

        boolean deleted = cartItemService.deleteItemFromCart(
                customerId,
                productId
        );

        return deleted
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CartResponse> getCartItem(
            Authentication authentication) {

        String googleSub = authentication.getName();

        CustomerResponse profile =
                customerService.getMyProfile(googleSub);

        String customerId = profile.getCustomerId();

        CartResponse cart =
                cartItemService.getCartResponse(customerId);

        return ResponseEntity.ok(cart);
    }
}
