package com.codewithsavage.majipoa.rider;

import com.codewithsavage.majipoa.rider.RiderDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints behind the rider screens. The rider is always the logged-in user
 * (see RiderService.currentRiderId), so there is no rider id in any URL.
 * Routes and payloads match RiderApiService in the Angular app.
 */
@RestController
@RequestMapping("/api/riders/me")
public class RiderPortalController {

    private final RiderService riders;
    private final DeliveryService deliveryService;

    public RiderPortalController(RiderService riders, DeliveryService deliveryService) {
        this.riders = riders;
        this.deliveryService = deliveryService;
    }

    private Long me(Authentication auth) {
        return riders.currentRiderId(auth);
    }

    // ---- request bodies sent by the Angular service --------------------------

    /** { "status": "AT_VENDOR" | "PICKED_UP" | "ARRIVED" | "DELIVERED" } */
    public record AdvanceRequest(@NotNull DeliveryStatus status) {}

    /** { "checked": true } */
    public record CheckItemRequest(boolean checked) {}

    /** { "online": true } */
    public record OnlineRequest(boolean online) {}

    // ---- Dashboard -----------------------------------------------------------

    @GetMapping("/dashboard")
    public DashboardDto dashboard(Authentication auth) {
        return riders.dashboard(me(auth));
    }

    @PutMapping("/status")
    public ProfileDto setStatus(Authentication auth, @RequestBody OnlineRequest body) {
        return riders.setOnline(me(auth), body.online());
    }

    @PutMapping("/location")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateLocation(Authentication auth, @Valid @RequestBody RiderLocationRequest body) {
        riders.updateLocation(me(auth), body);
    }

    @GetMapping("/offers/current")
    public ResponseEntity<OfferDto> currentOffer(Authentication auth) {
        return deliveryService.currentOffer(me(auth))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/deliveries/{deliveryId}/accept")
    public ActiveDeliveryDto accept(Authentication auth, @PathVariable Long deliveryId) {
        return deliveryService.accept(me(auth), deliveryId);
    }

    @PostMapping("/deliveries/{deliveryId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(Authentication auth, @PathVariable Long deliveryId) {
        deliveryService.decline(me(auth), deliveryId);
    }

    // ---- Active delivery -----------------------------------------------------

    /** 204 = no active delivery. */
    @GetMapping("/active-delivery")
    public ResponseEntity<ActiveDeliveryDto> active(Authentication auth) {
        return deliveryService.findActive(me(auth))
                .map(deliveryService::toActiveDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** The status must be the next step in the flow. */
    @PostMapping("/deliveries/{deliveryId}/advance")
    public ActiveDeliveryDto advance(Authentication auth,
                                     @PathVariable Long deliveryId,
                                     @Valid @RequestBody AdvanceRequest body) {
        return deliveryService.advance(me(auth), deliveryId, body.status());
    }

    @PatchMapping("/deliveries/{deliveryId}/items/{itemId}")
    public ActiveDeliveryDto checkItem(Authentication auth,
                                       @PathVariable Long deliveryId,
                                       @PathVariable Long itemId,
                                       @RequestBody CheckItemRequest body) {
        return deliveryService.checkItem(me(auth), deliveryId, itemId, body.checked());
    }

    // ---- History -------------------------------------------------------------

    /** period = today | week | month | all */
    @GetMapping("/history")
    public HistoryDto history(Authentication auth,
                              @RequestParam(defaultValue = "week") String period,
                              @RequestParam(name = "q", required = false) String q) {
        // Order numbers are shown as "#WM10248" but stored without the "#"
        String cleaned = q == null ? "" : q.trim().replaceFirst("^#", "");
        String search = cleaned.isBlank() ? null : cleaned;
        return riders.history(me(auth), period, search);
    }

    // ---- Earnings ------------------------------------------------------------

    /** range = this-week | last-week | this-month */
    @GetMapping("/earnings")
    public EarningsDto earnings(Authentication auth,
                                @RequestParam(name = "range", defaultValue = "this-week") String range) {
        return riders.earnings(me(auth), range);
    }

    // ---- Profile -------------------------------------------------------------

    @GetMapping("/profile")
    public ProfileDto profile(Authentication auth) {
        return riders.profile(me(auth));
    }

    @PutMapping("/profile")
    public ProfileDto updateProfile(Authentication auth, @Valid @RequestBody ProfileUpdateRequest request) {
        return riders.updateProfile(me(auth), request);
    }
}