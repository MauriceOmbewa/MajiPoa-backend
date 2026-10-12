package com.codewithsavage.majipoa.Customer.dto;

import lombok.Data;

@Data
public class CustomerRequest {
    private String firstName;
    private String lastName;
    private String email;
    private String userName;
    private String phoneNumber;
    private AddressDTO address;
}

