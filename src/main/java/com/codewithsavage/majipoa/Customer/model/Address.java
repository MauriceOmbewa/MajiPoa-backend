package com.codewithsavage.majipoa.Customer.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Addresses_Table")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long addressId;
    private String county;
    private String subCounty;
    private String area;
    private String street;
    private String building;
    private String landmark;
    private String pinLocation;
}