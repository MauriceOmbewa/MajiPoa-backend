package com.codewithsavage.majipoa.Customer.dto;

import com.codewithsavage.majipoa.shared.model.Role;
import lombok.Data;

/**
 * Response returned to the frontend for the authenticated customer.
 *
 * User identity information comes from AppUser, while
 * customer-specific information comes from Customer.
 */
@Data
public class CustomerResponse {

        private String customerId;
        private String email;
        private String firstName;
        private String lastName;
        private String userName;
        private String pictureUrl;
        private Role role;
        private String phoneNumber;
        private AddressDTO address;
}