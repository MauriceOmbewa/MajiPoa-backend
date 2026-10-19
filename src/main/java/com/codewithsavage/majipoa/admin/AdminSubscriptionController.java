package com.codewithsavage.majipoa.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/subscriptions")
@RequiredArgsConstructor
public class AdminSubscriptionController {

    private final AdminService adminService;

    @GetMapping
    public AdminDtos.SubscriptionsResponse getSubscriptions(
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) String search) {
        return adminService.getSubscriptions(tab, search);
    }

    @PostMapping("/{vendorId}/cancel")
    public AdminDtos.SubscriptionDto cancel(@PathVariable Long vendorId) {
        return adminService.cancelSubscription(vendorId);
    }

    @PostMapping("/{vendorId}/upgrade")
    public AdminDtos.SubscriptionDto upgrade(@PathVariable Long vendorId) {
        return adminService.upgradeSubscription(vendorId);
    }
}
