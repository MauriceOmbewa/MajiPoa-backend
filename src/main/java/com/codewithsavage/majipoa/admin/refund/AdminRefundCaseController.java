package com.codewithsavage.majipoa.admin.refund;

import com.codewithsavage.majipoa.admin.refund.RefundDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

/** Under /api/admin/**, so it needs ROLE_ADMIN and the verified admin password session. */
@RestController
@RequestMapping("/api/admin/refund-cases")
public class AdminRefundCaseController {

    private final AdminRefundCaseService service;

    public AdminRefundCaseController(AdminRefundCaseService service) {
        this.service = service;
    }

    @GetMapping
    public RefundPageResponse overview() {
        return service.overview();
    }

    @PostMapping("/{id}/decision")
    public RefundCaseView decide(@PathVariable Long id,
                                 @Valid @RequestBody DecisionRequest request,
                                 @AuthenticationPrincipal OidcUser admin) {
        return service.decide(id, request, who(admin));
    }

    @PostMapping("/{id}/mpesa-ref")
    public RefundCaseView addMpesaRef(@PathVariable Long id,
                                      @Valid @RequestBody MpesaRefRequest request,
                                      @AuthenticationPrincipal OidcUser admin) {
        return service.addMpesaRef(id, request.mpesaRef(), who(admin));
    }

    private String who(OidcUser admin) {
        return admin != null && admin.getEmail() != null ? admin.getEmail() : "admin";
    }
}