package com.codewithsavage.majipoa.admin.order;

import com.codewithsavage.majipoa.admin.order.AdminOrderDtos.AdminOrdersResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Under /api/admin/**, so it needs ROLE_ADMIN and the verified admin password session. */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final AdminOrderService service;

    public AdminOrderController(AdminOrderService service) {
        this.service = service;
    }

    @GetMapping
    public AdminOrdersResponse overview() {
        return service.overview();
    }
}