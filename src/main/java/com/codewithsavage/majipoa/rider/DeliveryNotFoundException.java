package com.codewithsavage.majipoa.rider;

public class DeliveryNotFoundException extends RuntimeException {

    public DeliveryNotFoundException(Long id) {
        super("Delivery not found with id " + id);
    }

    public DeliveryNotFoundException(String message) {
        super(message);
    }
}
