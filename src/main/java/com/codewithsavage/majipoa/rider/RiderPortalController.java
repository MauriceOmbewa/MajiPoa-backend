package com.codewithsavage.majipoa.rider;

import com.codewithsavage.majipoa.rider.RiderDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints behind the rider screens. Until login is added, the rider id comes from the URL;
 * once authentication exists, read it from the logged-in user instead.
 */
@RestController
@RequestMapping("/api/riders/{riderId}")
public class RiderPortalController {

    private final RiderPortalService portal;
    private final DeliveryService deliveryService;

    public RiderPortalController(RiderPortalService portal, DeliveryService deliveryService) {
        this.portal = portal;
        this.deliveryService = deliveryService;
    }

    // ---- Dashboard ----------------------------------------------------------

    @GetMapping("/dashboard")
    public DashboardDto dashboard(@PathVariable Long riderId) {
        return portal.dashboard(riderId);
    }

    @PatchMapping("/online")
    public ProfileDto setOnline(@PathVariable Long riderId, @RequestParam boolean online) {
        return portal.setOnline(riderId, online);
    }

    @GetMapping("/offers/current")
    public ResponseEntity<OfferDto> currentOffer(@PathVariable Long riderId) {
        return deliveryService.currentOffer(riderId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/deliveries/{deliveryId}/accept")
    public ActiveDeliveryDto accept(@PathVariable Long riderId, @PathVariable Long deliveryId) {
        return deliveryService.accept(riderId, deliveryId);
    }

    @PostMapping("/deliveries/{deliveryId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@PathVariable Long riderId, @PathVariable Long deliveryId) {
        deliveryService.decline(riderId, deliveryId);
    }

    // ---- Active delivery ----------------------------------------------------

    @GetMapping("/deliveries/active")
    public ResponseEntity<ActiveDeliveryDto> active(@PathVariable Long riderId) {
        return deliveryService.findActive(riderId)
                .map(deliveryService::toActiveDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** status = AT_VENDOR | PICKED_UP | ARRIVED | DELIVERED (must be the next step). */
    @PatchMapping("/deliveries/{deliveryId}/status")
    public ActiveDeliveryDto advance(@PathVariable Long riderId,
                                     @PathVariable Long deliveryId,
                                     @RequestParam DeliveryStatus status) {
        return deliveryService.advance(riderId, deliveryId, status);
    }

    @PatchMapping("/deliveries/{deliveryId}/items/{itemId}")
    public ActiveDeliveryDto checkItem(@PathVariable Long riderId,
                                       @PathVariable Long deliveryId,
                                       @PathVariable Long itemId,
                                       @RequestParam boolean checked) {
        return deliveryService.checkItem(riderId, deliveryId, itemId, checked);
    }

    // ---- History ------------------------------------------------------------

    /** period = today | week | month | all */
    @GetMapping("/history")
    public HistoryDto history(@PathVariable Long riderId,
                              @RequestParam(defaultValue = "week") String period,
                              @RequestParam(required = false) String search) {
        return portal.history(riderId, period, search);
    }

    // ---- Earnings -----------------------------------------------------------

    /** period = this-week | last-week | this-month */
    @GetMapping("/earnings")
    public EarningsDto earnings(@PathVariable Long riderId,
                                @RequestParam(defaultValue = "this-week") String period) {
        return portal.earnings(riderId, period);
    }

    // ---- Profile ------------------------------------------------------------

    @GetMapping("/profile")
    public ProfileDto profile(@PathVariable Long riderId) {
        return portal.profile(riderId);
    }

    @PutMapping("/profile")
    public ProfileDto updateProfile(@PathVariable Long riderId,
                                    @Valid @RequestBody ProfileUpdateRequest request) {
        return portal.updateProfile(riderId, request);
    }
}
