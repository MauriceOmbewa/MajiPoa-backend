package com.codewithsavage.majipoa.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/payouts")
@RequiredArgsConstructor
public class AdminPayoutController {

    private final AdminService adminService;

    @GetMapping
    public AdminDtos.PayoutsResponse getPayouts(
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) String search) {
        return adminService.getPayouts(tab, recipient, search);
    }

    @PostMapping("/run-settlement")
    public void runSettlement() {
        adminService.runSettlement();
    }

    @PostMapping("/{id}/hold")
    public AdminDtos.AdminPayoutDto hold(@PathVariable Long id) {
        return adminService.holdPayout(id);
    }

    @PostMapping("/{id}/release")
    public AdminDtos.AdminPayoutDto release(@PathVariable Long id) {
        return adminService.releasePayout(id);
    }
}
