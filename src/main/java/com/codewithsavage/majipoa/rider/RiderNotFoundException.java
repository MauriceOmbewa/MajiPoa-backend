package com.codewithsavage.majipoa.rider;

public class RiderNotFoundException extends RuntimeException {

    public RiderNotFoundException(Long id) {
        super("Rider not found with id " + id);
    }
}
