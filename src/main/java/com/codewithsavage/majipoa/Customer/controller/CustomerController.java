package com.codewithsavage.majipoa.Customer.controller;

import com.codewithsavage.majipoa.Customer.dto.CustomerRequest;
import com.codewithsavage.majipoa.Customer.dto.CustomerResponse;
import com.codewithsavage.majipoa.Customer.service.CustomerService;
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

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<CustomerResponse>> getAllCustomers() {
        return new ResponseEntity<>(customerService.fetchAllCustomers(), HttpStatus.OK);

    }

    /**
     * Returns the profile of the currently authenticated customer.
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<CustomerResponse> getMyProfile(
            Authentication authentication) {

        String googleSub = authentication.getName();

        CustomerResponse response =
                customerService.getMyProfile(googleSub);

        return ResponseEntity.ok(
                customerService.getMyProfile(authentication.getName())
        );
    }

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

    @GetMapping("/{customerId}")
    public ResponseEntity<CustomerResponse> getCustomer(@PathVariable String customerId) {
        return customerService.fetchCustomer(customerId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{customerId}")
    public ResponseEntity<String> updateCustomer(@PathVariable String customerId, @RequestBody CustomerRequest updatedCustomerRequest) {
        boolean updated = customerService.updateCustomer(customerId, updatedCustomerRequest);
        if ( updated) {
            return ResponseEntity.ok("Customer updated successfully");
        }
        return ResponseEntity.notFound().build();
    }
}