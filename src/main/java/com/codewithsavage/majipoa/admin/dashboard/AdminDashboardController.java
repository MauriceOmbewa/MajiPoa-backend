package com.codewithsavage.majipoa.admin.dashboard;

import com.codewithsavage.majipoa.admin.AdminDtos.DashboardResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Under /api/admin/**, so AdminGateInterceptor already requires an admin session. */
@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService service;

    public AdminDashboardController(AdminDashboardService service) {
        this.service = service;
    }

    @GetMapping
    public DashboardResponse dashboard() {
        return service.dashboard();
    }
}