package com.codewithsavage.majipoa.admin.rider;

import com.codewithsavage.majipoa.admin.rider.AdminRiderDtos.*;
import org.springframework.web.bind.annotation.*;

/**
 * Admin side of riders. Under /api/admin/**, so SecurityConfig requires ROLE_ADMIN and
 * AdminGateInterceptor requires the Google admin login plus the verified password.
 */
@RestController
@RequestMapping("/api/admin/riders")
public class AdminRiderController {

    private final AdminRiderService service;

    public AdminRiderController(AdminRiderService service) {
        this.service = service;
    }

    @GetMapping
    public AdminRidersResponse overview() {
        return service.overview();
    }

    @PostMapping("/{id}/approve")
    public AdminRiderRow approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/suspend")
    public AdminRiderRow suspend(@PathVariable Long id) {
        return service.suspend(id);
    }

    @PostMapping("/{id}/reinstate")
    public AdminRiderRow reinstate(@PathVariable Long id) {
        return service.reinstate(id);
    }
}