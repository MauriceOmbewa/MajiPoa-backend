package com.codewithsavage.majipoa.rider;

import java.time.LocalDateTime;

public class RiderResponse {

    private Long id;
    private String fullName;
    private String phone;
    private String email;
    private String vehicleType;
    private String plateNumber;
    private RiderStatus status;
    private Double latitude;
    private Double longitude;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static RiderResponse from(Rider r) {
        RiderResponse dto = new RiderResponse();
        dto.id = r.getId();
        dto.fullName = r.getFullName();
        dto.phone = r.getPhone();
        dto.email = r.getEmail();
        dto.vehicleType = r.getVehicleType();
        dto.plateNumber = r.getPlateNumber();
        dto.status = r.getStatus();
        dto.latitude = r.getLatitude();
        dto.longitude = r.getLongitude();
        dto.createdAt = r.getCreatedAt();
        dto.updatedAt = r.getUpdatedAt();
        return dto;
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getVehicleType() { return vehicleType; }
    public String getPlateNumber() { return plateNumber; }
    public RiderStatus getStatus() { return status; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
