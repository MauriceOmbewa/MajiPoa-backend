package com.codewithsavage.majipoa.Customer.dto;

import lombok.Data;

@Data
public class AddressDTO {
    //    private String addressId;
    private String county;
    private String subCounty;
    private String area;
    private String street;
    private String building;
    private String landmark;
    private String pinLocation;
}