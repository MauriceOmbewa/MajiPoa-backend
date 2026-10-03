package com.codewithsavage.majipoa.shared.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * A product (SKU) offered by a vendor — e.g. "20L refill", "500ml case".
 *
 * marketMin / marketMax represent the competitive price range across all vendors
 * in the same area, used to show a "Cheapest / Mid-range / Highest" label in the UI.
 *
 * availableToday / soldToday reset at the start of each day (future scheduled job).
 */
@Entity
@Table(name = "products")
@Getter @Setter @NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private VendorProfile vendor;

    @Column(nullable = false)
    private String name;

    private String note;
    private String size;

    private int price;       // KES
    private int marketMin;   // KES – lowest competitor price in area
    private int marketMax;   // KES – highest competitor price in area

    private int availableToday;
    private int soldToday;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status = ProductStatus.SELLING;

    public enum ProductStatus { SELLING, PAUSED, OUT_OF_STOCK }
}
