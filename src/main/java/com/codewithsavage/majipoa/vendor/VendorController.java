package com.codewithsavage.majipoa.vendor;

import com.codewithsavage.majipoa.shared.model.AppUser;
import com.codewithsavage.majipoa.shared.repository.UserRepository;
import com.codewithsavage.majipoa.vendor.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for the vendor portal — everything under /api/vendor/**.
 *
 * Access control: Spring Security enforces ROLE_VENDOR on the whole /api/vendor/**
 * path prefix (see SecurityConfig). The controller itself does not repeat that check.
 *
 * Pattern:
 *   1. @AuthenticationPrincipal OidcUser  → currentUser() → AppUser from DB
 *   2. AppUser passed to VendorService method
 *   3. VendorService returns a DTO
 *   4. Controller returns the DTO (Spring serializes to JSON)
 *
 * Endpoint map:
 *
 *   GET    /api/vendor/dashboard
 *
 *   GET    /api/vendor/orders
 *   GET    /api/vendor/orders/{ref}
 *   POST   /api/vendor/orders/{ref}/accept
 *   POST   /api/vendor/orders/{ref}/decline
 *   POST   /api/vendor/orders/{ref}/ready
 *   POST   /api/vendor/orders/{ref}/assign-rider
 *   POST   /api/vendor/orders/{ref}/deliver
 *
 *   GET    /api/vendor/products
 *   PATCH  /api/vendor/products/{id}/stock
 *   PATCH  /api/vendor/products/{id}/price
 *   POST   /api/vendor/products/{id}/toggle-pause
 *
 *   GET    /api/vendor/analytics?period=
 *
 *   GET    /api/vendor/offline-sales
 *   POST   /api/vendor/offline-sales
 *   DELETE /api/vendor/offline-sales/{id}
 *
 *   GET    /api/vendor/payouts
 *
 *   GET    /api/vendor/verification
 *   PUT    /api/vendor/verification/profile
 *
 *   GET    /api/vendor/subscription
 *   POST   /api/vendor/subscription/upgrade
 */
@RestController
@RequestMapping("/api/vendor")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService  vendorService;
    private final UserRepository users;

    private AppUser currentUser(OidcUser principal) {
        return users.findByGoogleSub(principal.getSubject())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in DB"));
    }

    // ── Dashboard ────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public VendorDashboardResponse dashboard(@AuthenticationPrincipal OidcUser principal) {
        return vendorService.getDashboard(currentUser(principal));
    }

    // ── Orders ───────────────────────────────────────────────────────────────

    @GetMapping("/orders")
    public List<VendorOrderDto> orders(@AuthenticationPrincipal OidcUser principal) {
        return vendorService.getOrders(currentUser(principal));
    }

    @GetMapping("/orders/{ref}")
    public VendorOrderDto order(@AuthenticationPrincipal OidcUser principal, @PathVariable String ref) {
        return vendorService.getOrder(currentUser(principal), ref);
    }

    @PostMapping("/orders/{ref}/accept")
    public VendorOrderDto acceptOrder(@AuthenticationPrincipal OidcUser principal, @PathVariable String ref) {
        return vendorService.acceptOrder(currentUser(principal), ref);
    }

    @PostMapping("/orders/{ref}/decline")
    public VendorOrderDto declineOrder(@AuthenticationPrincipal OidcUser principal, @PathVariable String ref) {
        return vendorService.declineOrder(currentUser(principal), ref);
    }

    @PostMapping("/orders/{ref}/ready")
    public VendorOrderDto markReady(@AuthenticationPrincipal OidcUser principal, @PathVariable String ref) {
        return vendorService.markReady(currentUser(principal), ref);
    }

    @PostMapping("/orders/{ref}/assign-rider")
    public VendorOrderDto assignRider(@AuthenticationPrincipal OidcUser principal,
                                      @PathVariable String ref,
                                      @Valid @RequestBody AssignRiderRequest body) {
        return vendorService.assignRider(currentUser(principal), ref, body);
    }

    @PostMapping("/orders/{ref}/deliver")
    public VendorOrderDto markDelivered(@AuthenticationPrincipal OidcUser principal, @PathVariable String ref) {
        return vendorService.markDelivered(currentUser(principal), ref);
    }

    // ── Products ─────────────────────────────────────────────────────────────

    @GetMapping("/products")
    public List<ProductDto> products(@AuthenticationPrincipal OidcUser principal) {
        return vendorService.getProducts(currentUser(principal));
    }

    @PatchMapping("/products/{id}/stock")
    public ProductDto updateStock(@AuthenticationPrincipal OidcUser principal,
                                  @PathVariable Long id, @RequestBody UpdateStockRequest body) {
        return vendorService.updateStock(currentUser(principal), id, body);
    }

    @PatchMapping("/products/{id}/price")
    public ProductDto updatePrice(@AuthenticationPrincipal OidcUser principal,
                                  @PathVariable Long id, @Valid @RequestBody UpdatePriceRequest body) {
        return vendorService.updatePrice(currentUser(principal), id, body);
    }

    @PostMapping("/products/{id}/toggle-pause")
    public ProductDto togglePause(@AuthenticationPrincipal OidcUser principal, @PathVariable Long id) {
        return vendorService.togglePause(currentUser(principal), id);
    }

    // ── Analytics ────────────────────────────────────────────────────────────

    @GetMapping("/analytics")
    public AnalyticsResponse analytics(@AuthenticationPrincipal OidcUser principal,
                                       @RequestParam(defaultValue = "Last 30 days") String period) {
        return vendorService.getAnalytics(currentUser(principal), period);
    }

    // ── Offline Sales ────────────────────────────────────────────────────────

    @GetMapping("/offline-sales")
    public List<OfflineSaleDto> offlineSales(@AuthenticationPrincipal OidcUser principal) {
        return vendorService.getOfflineSales(currentUser(principal));
    }

    @PostMapping("/offline-sales")
    @ResponseStatus(HttpStatus.CREATED)
    public OfflineSaleDto createOfflineSale(@AuthenticationPrincipal OidcUser principal,
                                            @Valid @RequestBody CreateOfflineSaleRequest body) {
        return vendorService.createOfflineSale(currentUser(principal), body);
    }

    @DeleteMapping("/offline-sales/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOfflineSale(@AuthenticationPrincipal OidcUser principal, @PathVariable Long id) {
        vendorService.deleteOfflineSale(currentUser(principal), id);
    }

    // ── Payouts ──────────────────────────────────────────────────────────────

    @GetMapping("/payouts")
    public PayoutsResponse payouts(@AuthenticationPrincipal OidcUser principal) {
        return vendorService.getPayouts(currentUser(principal));
    }

    // ── Verification / Business Profile ──────────────────────────────────────

    @GetMapping("/verification")
    public VendorVerificationResponse verification(@AuthenticationPrincipal OidcUser principal) {
        return vendorService.getVerification(currentUser(principal));
    }

    @PutMapping("/verification/profile")
    public VendorVerificationResponse updateProfile(@AuthenticationPrincipal OidcUser principal,
                                                    @Valid @RequestBody UpdateProfileRequest body) {
        return vendorService.updateProfile(currentUser(principal), body);
    }

    // ── Subscription ─────────────────────────────────────────────────────────

    @GetMapping("/subscription")
    public SubscriptionResponse subscription(@AuthenticationPrincipal OidcUser principal) {
        return vendorService.getSubscription(currentUser(principal));
    }

    @PostMapping("/subscription/upgrade")
    public SubscriptionResponse upgradeToPro(@AuthenticationPrincipal OidcUser principal) {
        return vendorService.upgradeToPro(currentUser(principal));
    }
}
