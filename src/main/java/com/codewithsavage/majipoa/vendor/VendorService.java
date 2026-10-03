package com.codewithsavage.majipoa.vendor;

import com.codewithsavage.majipoa.shared.model.*;
import com.codewithsavage.majipoa.shared.model.Product.ProductStatus;
import com.codewithsavage.majipoa.shared.model.VendorOrder.*;
import com.codewithsavage.majipoa.shared.repository.*;
import com.codewithsavage.majipoa.vendor.dto.*;
import com.codewithsavage.majipoa.vendor.dto.AnalyticsResponse.*;
import com.codewithsavage.majipoa.vendor.dto.PayoutsResponse.*;
import com.codewithsavage.majipoa.vendor.dto.VendorDashboardResponse.*;
import com.codewithsavage.majipoa.vendor.dto.VendorOrderDto.*;
import com.codewithsavage.majipoa.vendor.dto.VendorVerificationResponse.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * All business logic for the vendor portal.
 *
 * Every public method takes an AppUser as its first argument — the controller
 * resolves the user from the Spring Security session and passes it in.
 * This keeps security concerns in the controller and business logic here.
 *
 * Pattern used throughout:
 *   1. Resolve the vendor's VendorProfile from their AppUser (auto-created on first access)
 *   2. Validate ownership — reject if the resource belongs to a different vendor
 *   3. Apply the business change
 *   4. Map the entity to a DTO and return it
 */
@Service
@RequiredArgsConstructor
@Transactional
public class VendorService {

    private final VendorProfileRepository vendorProfiles;
    private final ProductRepository        products;
    private final VendorOrderRepository    orders;
    private final OfflineSaleRepository    offlineSales;
    private final VendorDocumentRepository documents;
    private final PayoutRecordRepository   payouts;

    // ── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Returns the VendorProfile for the user, creating a default one if this
     * is the first time the user accesses their vendor portal.
     */
    public VendorProfile getOrCreateProfile(AppUser user) {
        return vendorProfiles.findByUserId(user.getId())
                .orElseGet(() -> {
                    VendorProfile p = new VendorProfile();
                    p.setUser(user);
                    p.setBusinessName(user.getName() != null
                        ? user.getName() + "'s Water" : "My Water Business");
                    return vendorProfiles.save(p);
                });
    }

    private VendorProfile requireProfile(Long userId) {
        return vendorProfiles.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("Vendor profile not found"));
    }

    private Long vendorProfileIdFor(Long userId) {
        return requireProfile(userId).getId();
    }

    private VendorOrder requireOrder(String orderRef, Long userId) {
        VendorOrder order = orders.findByOrderRef(orderRef)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderRef));
        if (!order.getVendor().getId().equals(vendorProfileIdFor(userId))) {
            throw new SecurityException("Order does not belong to this vendor");
        }
        return order;
    }

    private String placedAgo(Instant placedAt) {
        long mins = ChronoUnit.MINUTES.between(placedAt, Instant.now());
        if (mins < 60) return mins + " min ago";
        long hrs = mins / 60;
        if (hrs < 24) return hrs + " hr ago";
        return (hrs / 24) + " days ago";
    }

    private long orderTotal(VendorOrder o) {
        return o.getLines().stream().mapToLong(l -> (long) l.getQty() * l.getUnitPrice()).sum();
    }

    private String itemsSummary(VendorOrder o) {
        return o.getLines().stream()
                .map(l -> l.getQty() + " × " + l.getProductName())
                .collect(Collectors.joining(", "));
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public VendorDashboardResponse getDashboard(AppUser user) {
        VendorProfile vendor = getOrCreateProfile(user);
        Long vid = vendor.getId();
        Instant startOfDay = LocalDate.now(ZoneId.of("Africa/Nairobi"))
                .atStartOfDay(ZoneId.of("Africa/Nairobi")).toInstant();

        long salesToday     = orders.sumSalesToday(vid, startOfDay);
        long ordersToday    = orders.countByVendorIdSince(vid, startOfDay);
        long completedToday = orders.findByVendorIdAndStatusOrderByPlacedAtDesc(vid, OrderStatus.COMPLETED)
                .stream().filter(o -> o.getDeliveredAt() != null && o.getDeliveredAt().isAfter(startOfDay))
                .count();

        int jugsInStock = products.findByVendorId(vid).stream()
                .filter(p -> p.getName() != null && p.getName().contains("20L"))
                .mapToInt(Product::getAvailableToday).sum();
        int jugsCapacity = jugsInStock + products.findByVendorId(vid).stream()
                .filter(p -> p.getName() != null && p.getName().contains("20L"))
                .mapToInt(Product::getSoldToday).sum();
        if (jugsCapacity == 0) jugsCapacity = 24;

        List<IncomingOrderDto> incoming = orders
                .findByVendorIdAndStatusOrderByPlacedAtDesc(vid, OrderStatus.NEW).stream()
                .map(o -> new IncomingOrderDto(o.getId(), o.getOrderRef(), itemsSummary(o),
                        o.getArea(), o.getDistanceKm(), orderTotal(o),
                        ChronoUnit.MINUTES.between(o.getPlacedAt(), Instant.now())))
                .collect(Collectors.toList());

        List<ActiveOrderDto> active = orders.findByVendorIdOrderByPlacedAtDesc(vid).stream()
                .filter(o -> o.getStatus() == OrderStatus.PREPARING
                          || o.getStatus() == OrderStatus.READY
                          || o.getStatus() == OrderStatus.OUT_FOR_DELIVERY)
                .map(o -> new ActiveOrderDto(o.getId(), o.getOrderRef(),
                        itemsSummary(o), o.getArea(), stageLabel(o)))
                .collect(Collectors.toList());

        return new VendorDashboardResponse(salesToday, ordersToday, completedToday,
                jugsInStock, jugsCapacity, incoming, active);
    }

    private String stageLabel(VendorOrder o) {
        return switch (o.getStatus()) {
            case PREPARING        -> "Preparing";
            case READY            -> "Rider assigned";
            case OUT_FOR_DELIVERY -> "On the way";
            default               -> o.getStatus().name();
        };
    }

    // ── Orders ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<VendorOrderDto> getOrders(AppUser user) {
        Long vid = getOrCreateProfile(user).getId();
        return orders.findByVendorIdOrderByPlacedAtDesc(vid)
                .stream().map(this::toOrderDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public VendorOrderDto getOrder(AppUser user, String orderRef) {
        return toOrderDto(requireOrder(orderRef, user.getId()));
    }

    public VendorOrderDto acceptOrder(AppUser user, String orderRef) {
        VendorOrder o = requireOrder(orderRef, user.getId());
        if (o.getStatus() != OrderStatus.NEW)
            throw new IllegalStateException("Order is not in NEW status");
        o.setStatus(OrderStatus.PREPARING);
        o.setAcceptedAt(Instant.now());
        return toOrderDto(orders.save(o));
    }

    public VendorOrderDto declineOrder(AppUser user, String orderRef) {
        VendorOrder o = requireOrder(orderRef, user.getId());
        if (o.getStatus() != OrderStatus.NEW)
            throw new IllegalStateException("Order is not in NEW status");
        o.setStatus(OrderStatus.CANCELLED);
        return toOrderDto(orders.save(o));
    }

    public VendorOrderDto markReady(AppUser user, String orderRef) {
        VendorOrder o = requireOrder(orderRef, user.getId());
        o.setStatus(OrderStatus.READY);
        return toOrderDto(orders.save(o));
    }

    public VendorOrderDto assignRider(AppUser user, String orderRef, AssignRiderRequest req) {
        VendorOrder o = requireOrder(orderRef, user.getId());
        o.setRiderName(req.riderName());
        o.setRiderKind(req.riderKind());
        o.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        o.setDeliveryStage(DeliveryStage.EN_ROUTE);
        return toOrderDto(orders.save(o));
    }

    public VendorOrderDto markDelivered(AppUser user, String orderRef) {
        VendorOrder o = requireOrder(orderRef, user.getId());
        o.setStatus(OrderStatus.COMPLETED);
        o.setDeliveredAt(Instant.now());
        o.getLines().forEach(line ->
            products.findByVendorId(getOrCreateProfile(user).getId()).stream()
                    .filter(p -> p.getName().equals(line.getProductName()))
                    .findFirst()
                    .ifPresent(p -> { p.setSoldToday(p.getSoldToday() + line.getQty()); products.save(p); })
        );
        return toOrderDto(orders.save(o));
    }

    private VendorOrderDto toOrderDto(VendorOrder o) {
        List<OrderLineDto> lineDtos = o.getLines().stream()
                .map(l -> new OrderLineDto(l.getProductName(), l.getNote(), l.getQty(), l.getUnitPrice()))
                .collect(Collectors.toList());
        return new VendorOrderDto(o.getId(), o.getOrderRef(), o.getCustomerName(),
                o.getArea(), o.getDistanceKm(), o.getStatus(), o.getPaymentStatus(),
                orderTotal(o), placedAgo(o.getPlacedAt()),
                o.getRiderName(), o.getRiderKind(), o.getDeliveryStage(),
                o.getPlacedAt(), lineDtos);
    }

    // ── Products ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProductDto> getProducts(AppUser user) {
        return products.findByVendorId(getOrCreateProfile(user).getId())
                .stream().map(this::toProductDto).collect(Collectors.toList());
    }

    public ProductDto updateStock(AppUser user, Long productId, UpdateStockRequest req) {
        Product p = requireProduct(productId, user);
        p.setAvailableToday(Math.max(0, p.getAvailableToday() + req.delta()));
        return toProductDto(products.save(p));
    }

    public ProductDto updatePrice(AppUser user, Long productId, UpdatePriceRequest req) {
        Product p = requireProduct(productId, user);
        p.setPrice(req.price());
        return toProductDto(products.save(p));
    }

    public ProductDto togglePause(AppUser user, Long productId) {
        Product p = requireProduct(productId, user);
        p.setStatus(p.getStatus() == ProductStatus.PAUSED ? ProductStatus.SELLING : ProductStatus.PAUSED);
        return toProductDto(products.save(p));
    }

    private Product requireProduct(Long productId, AppUser user) {
        Product p = products.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));
        if (!p.getVendor().getId().equals(getOrCreateProfile(user).getId()))
            throw new SecurityException("Product does not belong to this vendor");
        return p;
    }

    private ProductDto toProductDto(Product p) {
        return new ProductDto(p.getId(), p.getName(), p.getNote(), p.getSize(),
                p.getPrice(), p.getMarketMin(), p.getMarketMax(),
                p.getAvailableToday(), p.getSoldToday(), p.getStatus());
    }

    // ── Analytics ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(AppUser user, String period) {
        Long vid = getOrCreateProfile(user).getId();
        List<VendorOrder> completed = orders.findByVendorIdAndStatusOrderByPlacedAtDesc(vid, OrderStatus.COMPLETED);

        long gross      = completed.stream().mapToLong(this::orderTotal).sum();
        long orderCount = completed.size();
        long commission = Math.round(gross * 0.08);
        long net        = gross - commission;
        long avg        = orderCount > 0 ? gross / orderCount : 0;

        long newCust    = Math.round(orderCount * 0.33);
        long returning  = Math.round(orderCount * 0.67);
        int  repeatRate = orderCount > 0 ? (int) Math.round(returning * 100.0 / Math.max(1, orderCount)) : 0;

        long cancelled  = orders.findByVendorIdAndStatusOrderByPlacedAtDesc(vid, OrderStatus.CANCELLED).size();
        long total      = completed.size() + cancelled;
        int  acceptRate = total > 0 ? (int) Math.round(completed.size() * 100.0 / total) : 100;
        double cancelRate = total > 0 ? Math.round(cancelled * 100.0 / total * 10) / 10.0 : 0.0;

        return new AnalyticsResponse(period, gross, net, commission, orderCount, avg,
                buildDayBars(completed), buildHourBars(completed),
                buildProductBars(completed), buildAreaBars(completed),
                newCust, returning, repeatRate, Math.round(returning * 0.23), Math.round(newCust * 0.20),
                acceptRate, 13, 38, cancelRate);
    }

    private List<DayBar> buildDayBars(List<VendorOrder> completed) {
        var byDay = completed.stream().collect(Collectors.groupingBy(
                o -> LocalDate.ofInstant(o.getPlacedAt(), ZoneId.of("Africa/Nairobi")).getDayOfMonth(),
                Collectors.summingLong(this::orderTotal)));
        if (byDay.isEmpty()) return List.of(new DayBar("—", 0, false));
        long maxVal = byDay.values().stream().mapToLong(Long::longValue).max().orElse(1);
        return byDay.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).limit(13)
                .map(e -> new DayBar(String.valueOf(e.getKey()),
                        (int) Math.round(e.getValue() * 100.0 / maxVal), e.getValue() >= maxVal * 0.85))
                .collect(Collectors.toList());
    }

    private List<HourBar> buildHourBars(List<VendorOrder> completed) {
        record Slot(String label, int start, int end) {}
        var slots = List.of(new Slot("6 – 9 am",6,9),new Slot("9 am – 12 pm",9,12),
                new Slot("12 – 3 pm",12,15),new Slot("3 – 6 pm",15,18),new Slot("6 – 8 pm",18,20));
        java.util.Map<String,Long> counts = new java.util.LinkedHashMap<>();
        slots.forEach(s -> counts.put(s.label(), 0L));
        for (VendorOrder o : completed) {
            int hour = LocalTime.ofInstant(o.getPlacedAt(), ZoneId.of("Africa/Nairobi")).getHour();
            for (var s : slots) if (hour >= s.start() && hour < s.end()) { counts.merge(s.label(),1L,Long::sum); break; }
        }
        long maxC = counts.values().stream().mapToLong(Long::longValue).max().orElse(1);
        return counts.entrySet().stream()
                .map(e -> new HourBar(e.getKey(),(int) Math.round(e.getValue()*100.0/maxC),e.getValue().intValue()))
                .collect(Collectors.toList());
    }

    private List<ShareBar> buildProductBars(List<VendorOrder> completed) {
        var byProduct = completed.stream().flatMap(o -> o.getLines().stream())
                .collect(Collectors.groupingBy(OrderLine::getProductName, Collectors.summingLong(OrderLine::getQty)));
        long maxQ = byProduct.values().stream().mapToLong(Long::longValue).max().orElse(1);
        return byProduct.entrySet().stream().sorted((a,b) -> Long.compare(b.getValue(),a.getValue())).limit(5)
                .map(e -> new ShareBar(e.getKey(),(int)Math.round(e.getValue()*100.0/maxQ),String.valueOf(e.getValue())))
                .collect(Collectors.toList());
    }

    private List<ShareBar> buildAreaBars(List<VendorOrder> completed) {
        var byArea = completed.stream().collect(Collectors.groupingBy(
                o -> o.getArea() != null ? o.getArea() : "Unknown", Collectors.counting()));
        long maxA = byArea.values().stream().mapToLong(Long::longValue).max().orElse(1);
        return byArea.entrySet().stream().sorted((a,b) -> Long.compare(b.getValue(),a.getValue())).limit(5)
                .map(e -> { int pct=(int)Math.round(e.getValue()*100.0/maxA); return new ShareBar(e.getKey(),pct,pct+"%"); })
                .collect(Collectors.toList());
    }

    // ── Offline Sales ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<OfflineSaleDto> getOfflineSales(AppUser user) {
        return offlineSales.findByVendorIdOrderBySaleDateDesc(getOrCreateProfile(user).getId())
                .stream().map(this::toOfflineSaleDto).collect(Collectors.toList());
    }

    public OfflineSaleDto createOfflineSale(AppUser user, CreateOfflineSaleRequest req) {
        OfflineSale sale = new OfflineSale();
        sale.setVendor(getOrCreateProfile(user));
        sale.setSaleDate(req.saleDate()); sale.setProduct(req.product());
        sale.setQty(req.qty()); sale.setAmount(req.amount()); sale.setChannel(req.channel());
        return toOfflineSaleDto(offlineSales.save(sale));
    }

    public void deleteOfflineSale(AppUser user, Long saleId) {
        OfflineSale sale = offlineSales.findById(saleId)
                .orElseThrow(() -> new EntityNotFoundException("Offline sale not found: " + saleId));
        if (!sale.getVendor().getId().equals(getOrCreateProfile(user).getId()))
            throw new SecurityException("Sale does not belong to this vendor");
        offlineSales.delete(sale);
    }

    private OfflineSaleDto toOfflineSaleDto(OfflineSale s) {
        return new OfflineSaleDto(s.getId(), s.getSaleDate(), s.getProduct(), s.getQty(), s.getAmount(), s.getChannel());
    }

    // ── Payouts ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PayoutsResponse getPayouts(AppUser user) {
        Long vid = getOrCreateProfile(user).getId();
        List<VendorOrder> completed = orders.findByVendorIdAndStatusOrderByPlacedAtDesc(vid, OrderStatus.COMPLETED);

        Instant weekAgo = LocalDate.now(ZoneId.of("Africa/Nairobi")).minusDays(7)
                .atStartOfDay(ZoneId.of("Africa/Nairobi")).toInstant();
        long grossPending = completed.stream()
                .filter(o -> o.getDeliveredAt() != null && o.getDeliveredAt().isAfter(weekAgo))
                .mapToLong(this::orderTotal).sum();
        long commission  = Math.round(grossPending * 0.08);
        long deliveryFees= completed.stream().filter(o -> o.getRiderKind() == RiderKind.PLATFORM).count() * 80L;
        long nextPayout  = Math.max(0, grossPending - commission - deliveryFees);
        long paidThisMonth = payouts.sumPaidSince(vid,
                LocalDate.now(ZoneId.of("Africa/Nairobi")).withDayOfMonth(1));

        List<SettlementLine> settlement = List.of(
                new SettlementLine("Sales on delivered orders (" + completed.size() + ")", grossPending),
                new SettlementLine("Platform commission · 8%", -commission),
                new SettlementLine("Delivery fees paid to platform riders", -deliveryFees),
                new SettlementLine("Refunds (estimated)", 0L));
        long payable = settlement.stream().mapToLong(SettlementLine::amount).sum();

        List<PayoutRecordDto> history = payouts.findByVendorIdOrderByPaidOnDesc(vid).stream()
                .map(p -> new PayoutRecordDto(p.getId(), p.getPaidOn(), p.getPeriodStart(),
                        p.getPeriodEnd(), p.getOrders(), p.getGross(), p.getDeductions(), p.getPaid(), p.getReference()))
                .collect(Collectors.toList());

        return new PayoutsResponse(nextPayout, 0L, paidThisMonth, 0L, settlement, payable, history);
    }

    // ── Verification / Profile ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public VendorVerificationResponse getVerification(AppUser user) {
        VendorProfile vendor = getOrCreateProfile(user);
        List<VendorDocumentDto> docs = documents.findByVendorId(vendor.getId()).stream()
                .map(d -> new VendorDocumentDto(d.getId(), d.getName(), d.getNote(),
                        d.getReference(), d.getSubmittedOn(), d.getValidUntil(), d.getStatus()))
                .collect(Collectors.toList());
        return new VendorVerificationResponse(vendor.getBusinessName(), vendor.getWaterSource(),
                vendor.getTreatment(), vendor.getPhValue(), vendor.getTdsValue(), vendor.getAbout(),
                vendor.getVerificationStatus(), vendor.getPlan(), docs);
    }

    public VendorVerificationResponse updateProfile(AppUser user, UpdateProfileRequest req) {
        VendorProfile vendor = getOrCreateProfile(user);
        vendor.setBusinessName(req.businessName()); vendor.setWaterSource(req.waterSource());
        vendor.setTreatment(req.treatment()); vendor.setPhValue(req.phValue());
        vendor.setTdsValue(req.tdsValue()); vendor.setAbout(req.about());
        vendorProfiles.save(vendor);
        return getVerification(user);
    }

    // ── Subscription ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SubscriptionResponse getSubscription(AppUser user) {
        return new SubscriptionResponse(getOrCreateProfile(user).getPlan());
    }

    public SubscriptionResponse upgradeToPro(AppUser user) {
        VendorProfile vendor = getOrCreateProfile(user);
        vendor.setPlan(VendorProfile.SubscriptionPlan.PRO);
        vendorProfiles.save(vendor);
        return new SubscriptionResponse(vendor.getPlan());
    }
}
