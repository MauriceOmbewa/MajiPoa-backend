package com.codewithsavage.majipoa.rider;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/riders")
public class RiderController {

    private final RiderService service;

    public RiderController(RiderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<RiderResponse> create(@Valid @RequestBody RiderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public List<RiderResponse> list(@RequestParam(required = false) RiderStatus status) {
        return service.findAll(status);
    }

    @GetMapping("/{id}")
    public RiderResponse get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    public RiderResponse update(@PathVariable Long id, @Valid @RequestBody RiderRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public RiderResponse updateStatus(@PathVariable Long id, @RequestParam RiderStatus status) {
        return service.updateStatus(id, status);
    }

    @PatchMapping("/{id}/location")
    public RiderResponse updateLocation(@PathVariable Long id,
                                        @Valid @RequestBody RiderLocationRequest location) {
        return service.updateLocation(id, location);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
