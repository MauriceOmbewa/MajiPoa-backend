
package com.codewithsavage.majipoa.Customer.controller;

import com.codewithsavage.majipoa.Customer.dto.CustomerRequest;
import com.codewithsavage.majipoa.Customer.dto.CustomerResponse;
import com.codewithsavage.majipoa.Customer.model.Customer;
import com.codewithsavage.majipoa.Customer.service.CustomerService;
import com.codewithsavage.majipoa.shared.dto.LocationRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CustomerResponse>> getAllCustomers() {
        return ResponseEntity.ok(customerService.fetchAllCustomers());
    }

    /**
     * Returns the authenticated customer's profile and saved address.
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CustomerResponse> getMyProfile(
            Authentication authentication) {

        CustomerResponse response =
                customerService.getMyProfile(authentication.getName());

        return ResponseEntity.ok(response);
    }

    /**
     * Updates the authenticated customer's profile and address.
     */
    @PutMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CustomerResponse> updateMyProfile(
            Authentication authentication,
            @RequestBody CustomerRequest request) {

        CustomerResponse response = customerService.updateMyProfile(
                authentication.getName(),
                request
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Called by the customer app after the browser's location permission is granted.
     */
    @PutMapping("/location")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> updateLocation(
            Authentication authentication,
            @Valid @RequestBody LocationRequest request) {

        customerService.updateLocation(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{customerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CustomerResponse> getCustomer(
            @PathVariable String customerId) {

        return customerService.fetchCustomer(customerId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Administrator-only customer update.
     */
    @PutMapping("/{customerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> updateCustomer(
            @PathVariable String customerId,
            @RequestBody CustomerRequest updatedCustomerRequest) {

        boolean updated = customerService.updateCustomer(
                customerId,
                updatedCustomerRequest
        );

        if (updated) {
            return ResponseEntity.ok("Customer updated successfully");
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
