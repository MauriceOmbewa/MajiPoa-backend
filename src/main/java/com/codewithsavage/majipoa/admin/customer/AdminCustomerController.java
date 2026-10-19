package com.codewithsavage.majipoa.admin.customer;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCustomerController {

    private final AdminCustomerService service;

    @GetMapping
    public AdminCustomerDtos.CustomersPageResponse list(
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String search) {
        return service.getPage(tab, area, search);
    }

    @GetMapping("/stats")
    public AdminCustomerDtos.StatsDto stats() {
        return service.getStats();
    }

    @GetMapping("/top")
    public List<AdminCustomerDtos.TopCustomerDto> top(@RequestParam(defaultValue = "5") int limit) {
        return service.getTop(limit);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String search) {
        String csv = service.exportCsv(tab, area, search);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"customers.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/{id}")
    public AdminCustomerDtos.CustomerDetailDto detail(@PathVariable String id) {
        return service.getDetail(id);
    }

    @PostMapping("/{id}/block")
    public AdminCustomerDtos.CustomerDetailDto block(
            @PathVariable String id,
            @RequestBody(required = false) AdminCustomerDtos.BlockRequest body,
            Authentication authentication) {
        return service.block(id, body == null ? null : body.reason(), authentication.getName());
    }

    @PostMapping("/{id}/unblock")
    public AdminCustomerDtos.CustomerDetailDto unblock(@PathVariable String id) {
        return service.unblock(id);
    }
}