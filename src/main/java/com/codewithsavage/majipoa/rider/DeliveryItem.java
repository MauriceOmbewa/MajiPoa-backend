package com.codewithsavage.majipoa.rider;

import jakarta.persistence.*;

/** One line of an order, e.g. "2 x 20L refill - hard jug" with the note "Collect 2 empty jugs". */
@Entity
@Table(name = "delivery_items")
public class DeliveryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id")
    private Delivery delivery;

    private int quantity = 1;

    @Column(nullable = false)
    private String name;

    private String note;

    /** Ticked by the rider at the vendor before pickup can be confirmed. */
    private boolean checked;

    public Long getId() { return id; }

    public Delivery getDelivery() { return delivery; }
    public void setDelivery(Delivery delivery) { this.delivery = delivery; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public boolean isChecked() { return checked; }
    public void setChecked(boolean checked) { this.checked = checked; }
}
