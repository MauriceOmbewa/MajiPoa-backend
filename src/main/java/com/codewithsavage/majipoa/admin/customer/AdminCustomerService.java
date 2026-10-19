package com.codewithsavage.majipoa.admin.customer;

import com.codewithsavage.majipoa.Customer.model.Address;
import com.codewithsavage.majipoa.Customer.model.Customer;
import com.codewithsavage.majipoa.Customer.repository.CustomerRepository;
import com.codewithsavage.majipoa.admin.customer.CustomerOrderSource.OrderFact;
import com.codewithsavage.majipoa.shared.model.Role;
import com.codewithsavage.majipoa.shared.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Every value here comes from the database (customers, orders, blocks).
 * Nothing is seeded or hard-coded.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCustomerService {

    private static final int AT_RISK_DAYS = 14;
    private static final double WEEKLY_GAP_DAYS = 7.0;

    private final CustomerRepository customerRepo;
    private final CustomerBlockRepository blockRepo;
    private final CustomerOrderSource orderSource;
    private final UserRepository userRepo;

    // ─── Table ───

    public AdminCustomerDtos.CustomersPageResponse getPage(String tab, String area, String search) {
        List<AdminCustomerDtos.CustomerRowDto> all = buildRows();
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);

        AdminCustomerDtos.TabCountsDto counts = new AdminCustomerDtos.TabCountsDto(
                all.size(),
                all.stream().filter(r -> r.joinedAt() != null && r.joinedAt().isAfter(weekAgo)).count(),
                all.stream().filter(r -> "AT_RISK".equals(r.status())).count(),
                all.stream().filter(r -> "BLOCKED".equals(r.status())).count());

        List<String> areas = all.stream()
                .map(AdminCustomerService::areaOf)
                .filter(Objects::nonNull)
                .distinct().sorted().toList();

        List<AdminCustomerDtos.CustomerRowDto> filtered = applyFilters(all, tab, area, search);
        return new AdminCustomerDtos.CustomersPageResponse(filtered, filtered.size(), counts, areas);
    }

    // ─── Stat cards ───

    public AdminCustomerDtos.StatsDto getStats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since30 = now.minusDays(30);
        LocalDate today = now.toLocalDate();

        List<OrderFact> facts = validFacts();

        Map<String, Long> orders30ByCustomer = facts.stream()
                .filter(f -> f.placedAt() != null && f.placedAt().isAfter(since30))
                .collect(Collectors.groupingBy(OrderFact::customerId, Collectors.counting()));

        long active30 = orders30ByCustomer.size();
        long repeaters = orders30ByCustomer.values().stream().filter(n -> n >= 2).count();
        int repeatRate = active30 == 0 ? 0 : (int) Math.round(repeaters * 100.0 / active30);

        List<OrderFact> todays = facts.stream()
                .filter(f -> f.placedAt() != null && f.placedAt().toLocalDate().equals(today))
                .toList();

        long newCustomers30 = customerRepo.findAll().stream()
                .filter(c -> c.getCreatedAt() != null && c.getCreatedAt().isAfter(since30))
                .count();

        return new AdminCustomerDtos.StatsDto(
                active30,
                newCustomers30,
                todays.size(),
                todays.stream().map(OrderFact::customerId).distinct().count(),
                repeatRate,
                blockRepo.count());
    }

    // ─── Top customers ───

    public List<AdminCustomerDtos.TopCustomerDto> getTop(int limit) {
        List<AdminCustomerDtos.CustomerRowDto> top = buildRows().stream()
                .filter(r -> r.totalSpend().signum() > 0)
                .sorted(Comparator.comparing(AdminCustomerDtos.CustomerRowDto::totalSpend).reversed())
                .limit(Math.max(1, limit))
                .toList();

        List<AdminCustomerDtos.TopCustomerDto> out = new ArrayList<>();
        for (int i = 0; i < top.size(); i++) {
            AdminCustomerDtos.CustomerRowDto r = top.get(i);
            out.add(new AdminCustomerDtos.TopCustomerDto(
                    i + 1, r.customerId(), r.firstName(), r.lastName(), r.userName(),
                    areaOf(r), r.totalSpend()));
        }
        return out;
    }

    // ─── Detail ───

    public AdminCustomerDtos.CustomerDetailDto getDetail(String id) {
        return toDetail(find(id));
    }

    // ─── Block / unblock ───

    @Transactional
    public AdminCustomerDtos.CustomerDetailDto block(String id, String reason, String adminGoogleSub) {
        Customer c = find(id);
        blockRepo.save(CustomerBlock.builder()
                .customerId(c.getCustomerId())
                .reason(reason == null || reason.isBlank() ? null : reason.trim())
                .blockedAt(LocalDateTime.now())
                .blockedBy(adminLabel(adminGoogleSub))
                .build());
        return toDetail(c);
    }

    @Transactional
    public AdminCustomerDtos.CustomerDetailDto unblock(String id) {
        Customer c = find(id);
        blockRepo.deleteById(c.getCustomerId());
        blockRepo.flush();
        return toDetail(c);
    }

    // ─── Export ───

    public String exportCsv(String tab, String area, String search) {
        List<AdminCustomerDtos.CustomerRowDto> rows = applyFilters(buildRows(), tab, area, search);
        StringBuilder sb = new StringBuilder();
        sb.append("Customer,Email,Phone,Area,Joined,Orders,Total spend (KSh),Last order,Status\n");
        for (AdminCustomerDtos.CustomerRowDto r : rows) {
            sb.append(csv(fullName(r))).append(',')
              .append(csv(r.email())).append(',')
              .append(csv(r.phoneNumber())).append(',')
              .append(csv(areaOf(r))).append(',')
              .append(r.joinedAt() != null ? r.joinedAt().toLocalDate() : "").append(',')
              .append(r.orders()).append(',')
              .append(r.totalSpend()).append(',')
              .append(r.lastOrderAt() != null ? r.lastOrderAt() : "").append(',')
              .append(r.status()).append('\n');
        }
        return sb.toString();
    }

    // ─── Row building ───

    private List<AdminCustomerDtos.CustomerRowDto> buildRows() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, List<OrderFact>> byCustomer = validFacts().stream()
                .collect(Collectors.groupingBy(OrderFact::customerId));
        Set<String> blockedIds = blockRepo.findAll().stream()
                .map(CustomerBlock::getCustomerId)
                .collect(Collectors.toSet());

        return customerRepo.findAll().stream()
                .map(c -> toRow(c,
                        byCustomer.getOrDefault(c.getCustomerId(), List.of()),
                        blockedIds.contains(c.getCustomerId()),
                        now))
                .sorted(Comparator.comparing(AdminCustomerDtos.CustomerRowDto::lastOrderAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private AdminCustomerDtos.CustomerRowDto toRow(Customer c, List<OrderFact> orders,
                                                   boolean blocked, LocalDateTime now) {
        BigDecimal spend = orders.stream()
                .map(o -> o.amount() == null ? BigDecimal.ZERO : o.amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime last = orders.stream().map(OrderFact::placedAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        LocalDateTime first = orders.stream().map(OrderFact::placedAt).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);

        return new AdminCustomerDtos.CustomerRowDto(
                c.getCustomerId(),
                c.getEmail(),
                c.getFirstName(),
                c.getLastName(),
                c.getUserName(),
                c.getUser() != null ? c.getUser().getPictureUrl() : null,
                c.getRole() == null ? Role.CUSTOMER.name() : c.getRole().name(),
                c.getPhoneNumber(),
                toAddress(c.getAddress()),
                c.getCreatedAt(),
                orders.size(),
                spend,
                last,
                statusOf(blocked, orders.size(), first, last, now));
    }

    private static AdminCustomerDtos.AddressDto toAddress(Address a) {
        if (a == null) return null;
        return new AdminCustomerDtos.AddressDto(
                a.getCounty(), a.getSubCounty(), a.getArea(), a.getStreet(),
                a.getBuilding(), a.getLandmark(), a.getPinLocation());
    }

    private String statusOf(boolean blocked, int orderCount,
                            LocalDateTime first, LocalDateTime last, LocalDateTime now) {
        if (blocked) return "BLOCKED";
        if (orderCount >= 2 && first != null && last != null
                && last.isBefore(now.minusDays(AT_RISK_DAYS))) {
            double gapDays = Duration.between(first, last).toHours() / 24.0 / (orderCount - 1);
            if (gapDays <= WEEKLY_GAP_DAYS) return "AT_RISK";
        }
        return "ACTIVE";
    }

    // ─── Filtering ───

    private List<AdminCustomerDtos.CustomerRowDto> applyFilters(
            List<AdminCustomerDtos.CustomerRowDto> rows, String tab, String area, String search) {

        String q = search == null ? "" : search.trim().toLowerCase();
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        String t = tab == null ? "" : tab.trim().toUpperCase();

        return rows.stream()
                .filter(r -> switch (t) {
                    case "NEW" -> r.joinedAt() != null && r.joinedAt().isAfter(weekAgo);
                    case "AT_RISK", "BLOCKED" -> r.status().equals(t);
                    default -> true;
                })
                .filter(r -> isBlank(area) || (areaOf(r) != null && areaOf(r).equalsIgnoreCase(area)))
                .filter(r -> q.isEmpty()
                        || contains(fullName(r), q)
                        || contains(r.userName(), q)
                        || contains(r.email(), q)
                        || contains(r.phoneNumber(), q))
                .toList();
    }

    // ─── Helpers ───

    private List<OrderFact> validFacts() {
        return orderSource.loadAll().stream()
                .filter(f -> f.customerId() != null && !f.cancelled())
                .toList();
    }

    private Customer find(String id) {
        return customerRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
    }

    private AdminCustomerDtos.CustomerDetailDto toDetail(Customer c) {
        AdminCustomerDtos.CustomerRowDto row = buildRows().stream()
                .filter(r -> r.customerId().equals(c.getCustomerId())).findFirst()
                .orElseGet(() -> toRow(c, List.of(), false, LocalDateTime.now()));
        CustomerBlock b = blockRepo.findById(c.getCustomerId()).orElse(null);
        return new AdminCustomerDtos.CustomerDetailDto(
                row,
                b != null ? b.getReason() : null,
                b != null ? b.getBlockedAt() : null,
                b != null ? b.getBlockedBy() : null);
    }

    private String adminLabel(String googleSub) {
        return userRepo.findByGoogleSub(googleSub)
                .map(u -> u.getName() != null ? u.getName() : u.getEmail())
                .orElse(googleSub);
    }

    private static String fullName(AdminCustomerDtos.CustomerRowDto r) {
        String full = ((r.firstName() == null ? "" : r.firstName()) + " "
                + (r.lastName() == null ? "" : r.lastName())).trim();
        if (!full.isEmpty()) return full;
        if (!isBlank(r.userName())) return r.userName();
        return r.email();
    }

    private static String areaOf(AdminCustomerDtos.CustomerRowDto r) {
        AdminCustomerDtos.AddressDto a = r.address();
        return a != null && !isBlank(a.area()) ? a.area() : null;
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static boolean contains(String v, String lowerQ) {
        return v != null && v.toLowerCase().contains(lowerQ);
    }

    private static String csv(String v) {
        return v == null ? "" : "\"" + v.replace("\"", "\"\"") + "\"";
    }
}