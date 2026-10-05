package com.codewithsavage.majipoa.rider;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "riders")
public class Rider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(unique = true)
    private String email;

    private String vehicleType;

    @Column(unique = true)
    private String plateNumber;

    /** How many 20L jerrycans the vehicle can carry. */
    private Integer capacity = 4;

    /** Number weekly earnings are paid to. */
    private String mpesaNumber;

    private boolean verified = false;

    private boolean notifyNewOrders = true;
    private boolean notifyPayouts = true;
    private boolean notifySms = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiderStatus status = RiderStatus.OFFLINE;

    /** Start of the current online session; null while offline. */
    private LocalDateTime onlineSince;

    private Double latitude;
    private Double longitude;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }

    public String getMpesaNumber() { return mpesaNumber; }
    public void setMpesaNumber(String mpesaNumber) { this.mpesaNumber = mpesaNumber; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    public boolean isNotifyNewOrders() { return notifyNewOrders; }
    public void setNotifyNewOrders(boolean notifyNewOrders) { this.notifyNewOrders = notifyNewOrders; }

    public boolean isNotifyPayouts() { return notifyPayouts; }
    public void setNotifyPayouts(boolean notifyPayouts) { this.notifyPayouts = notifyPayouts; }

    public boolean isNotifySms() { return notifySms; }
    public void setNotifySms(boolean notifySms) { this.notifySms = notifySms; }

    public RiderStatus getStatus() { return status; }

    /** Keeps onlineSince in step with the status, so "Online 6h 12m" can be computed. */
    public void setStatus(RiderStatus status) {
        if (status == RiderStatus.OFFLINE) {
            this.onlineSince = null;
        } else if (this.status == RiderStatus.OFFLINE || this.onlineSince == null) {
            this.onlineSince = LocalDateTime.now();
        }
        this.status = status;
    }

    public LocalDateTime getOnlineSince() { return onlineSince; }
    public void setOnlineSince(LocalDateTime onlineSince) { this.onlineSince = onlineSince; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
