package com.codewithsavage.majipoa.rider;

import com.codewithsavage.majipoa.rider.RiderDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Calls made by the customer/order side rather than by the rider app. */
@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {

    private final DeliveryService service;

    public DeliveryController(DeliveryService service) {
        this.service = service;
    }

    /** Offer a delivery to a rider. The rider then sees it as "New delivery offer". */
    @PostMapping("/offers")
    public ResponseEntity<OfferDto> createOffer(@Valid @RequestBody CreateOfferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createOffer(request));
    }

    /** Customer rates a delivered order, which feeds the rider's star rating. */
    @PostMapping("/{deliveryId}/rating")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rate(@PathVariable Long deliveryId, @Valid @RequestBody RatingRequest request) {
        service.rate(deliveryId, request.rating());
    }
}
