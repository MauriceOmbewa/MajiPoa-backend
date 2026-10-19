package com.codewithsavage.majipoa.rider;

/** Thrown when an action does not fit the delivery's (or rider's) current state. */
public class InvalidDeliveryStateException extends RuntimeException {

    public InvalidDeliveryStateException(String message) {
        super(message);
    }
}
