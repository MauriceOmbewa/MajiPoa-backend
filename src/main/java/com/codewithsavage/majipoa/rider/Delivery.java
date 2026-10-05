package com.codewithsavage.majipoa.rider;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "deliveries")
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stored without the leading '#', e.g. WM10248. */
    @Column(nullable = false, unique = true)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rider_id")
    private Rider rider;

    // ---- pickup (vendor) ----
    private String vendorName;
    private String pickupAddress;
    private Double pickupLat;
    private Double pickupLng;

    // ---- drop-off (customer) ----
    private String customerName;
    private String customerPhone;
    private String dropoffAddress;   // street, e.g. "Argwings Kodhek Rd, Kilimani"
    private String dropoffUnit;      // building/unit, e.g. "Riverside Court, B12, 3rd floor"
    private String dropoffArea;      // short area, e.g. "Kilimani"
    private Double dropoffLat;
    private Double dropoffLng;

    @Column(length = 500)
    private String dropoffNotes;

    // ---- trip ----
    private double distanceToPickupKm;
    private double pickupToDropoffKm;
    private int durationMin;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal fee;

    private boolean paid = true;
    private boolean heavyLoad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus status = DeliveryStatus.OFFERED;

    // ---- timeline ----
    private LocalDateTime offeredAt;
    private LocalDateTime offerExpiresAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime arrivedAtVendorAt;
    private LocalDateTime pickedUpAt;
    private LocalDateTime arrivedAtCustomerAt;
    private LocalDateTime deliveredAt;

    private Boolean onTime;
    private Integer rating;

    @OneToMany(mappedBy = "delivery", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<DeliveryItem> items = new ArrayList<>();

    public void addItem(DeliveryItem item) {
        item.setDelivery(this);
        items.add(item);
    }

    public Long getId() { return id; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public Rider getRider() { return rider; }
    public void setRider(Rider rider) { this.rider = rider; }

    public String getVendorName() { return vendorName; }
    public void setVendorName(String vendorName) { this.vendorName = vendorName; }

    public String getPickupAddress() { return pickupAddress; }
    public void setPickupAddress(String pickupAddress) { this.pickupAddress = pickupAddress; }

    public Double getPickupLat() { return pickupLat; }
    public void setPickupLat(Double pickupLat) { this.pickupLat = pickupLat; }

    public Double getPickupLng() { return pickupLng; }
    public void setPickupLng(Double pickupLng) { this.pickupLng = pickupLng; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getDropoffAddress() { return dropoffAddress; }
    public void setDropoffAddress(String dropoffAddress) { this.dropoffAddress = dropoffAddress; }

    public String getDropoffUnit() { return dropoffUnit; }
    public void setDropoffUnit(String dropoffUnit) { this.dropoffUnit = dropoffUnit; }

    public String getDropoffArea() { return dropoffArea; }
    public void setDropoffArea(String dropoffArea) { this.dropoffArea = dropoffArea; }

    public Double getDropoffLat() { return dropoffLat; }
    public void setDropoffLat(Double dropoffLat) { this.dropoffLat = dropoffLat; }

    public Double getDropoffLng() { return dropoffLng; }
    public void setDropoffLng(Double dropoffLng) { this.dropoffLng = dropoffLng; }

    public String getDropoffNotes() { return dropoffNotes; }
    public void setDropoffNotes(String dropoffNotes) { this.dropoffNotes = dropoffNotes; }

    public double getDistanceToPickupKm() { return distanceToPickupKm; }
    public void setDistanceToPickupKm(double distanceToPickupKm) { this.distanceToPickupKm = distanceToPickupKm; }

    public double getPickupToDropoffKm() { return pickupToDropoffKm; }
    public void setPickupToDropoffKm(double pickupToDropoffKm) { this.pickupToDropoffKm = pickupToDropoffKm; }

    public int getDurationMin() { return durationMin; }
    public void setDurationMin(int durationMin) { this.durationMin = durationMin; }

    public BigDecimal getFee() { return fee; }
    public void setFee(BigDecimal fee) { this.fee = fee; }

    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }

    public boolean isHeavyLoad() { return heavyLoad; }
    public void setHeavyLoad(boolean heavyLoad) { this.heavyLoad = heavyLoad; }

    public DeliveryStatus getStatus() { return status; }
    public void setStatus(DeliveryStatus status) { this.status = status; }

    public LocalDateTime getOfferedAt() { return offeredAt; }
    public void setOfferedAt(LocalDateTime offeredAt) { this.offeredAt = offeredAt; }

    public LocalDateTime getOfferExpiresAt() { return offerExpiresAt; }
    public void setOfferExpiresAt(LocalDateTime offerExpiresAt) { this.offerExpiresAt = offerExpiresAt; }

    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }

    public LocalDateTime getArrivedAtVendorAt() { return arrivedAtVendorAt; }
    public void setArrivedAtVendorAt(LocalDateTime arrivedAtVendorAt) { this.arrivedAtVendorAt = arrivedAtVendorAt; }

    public LocalDateTime getPickedUpAt() { return pickedUpAt; }
    public void setPickedUpAt(LocalDateTime pickedUpAt) { this.pickedUpAt = pickedUpAt; }

    public LocalDateTime getArrivedAtCustomerAt() { return arrivedAtCustomerAt; }
    public void setArrivedAtCustomerAt(LocalDateTime arrivedAtCustomerAt) { this.arrivedAtCustomerAt = arrivedAtCustomerAt; }

    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }

    public Boolean getOnTime() { return onTime; }
    public void setOnTime(Boolean onTime) { this.onTime = onTime; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public List<DeliveryItem> getItems() { return items; }
}
