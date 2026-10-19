package com.codewithsavage.majipoa.admin.customer;

import com.codewithsavage.majipoa.Customer.model.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CustomerOrderSource {

    private final AdminOrderFactRepository orderFacts;

    public record OrderFact(
            String customerId,
            BigDecimal amount,
            LocalDateTime placedAt,
            boolean cancelled
    ) {}

    public List<OrderFact> loadAll() {
        return orderFacts.findAllFacts().stream()
                .map(v -> new OrderFact(
                        v.getCustomerId(),
                        v.getAmount(),
                        v.getPlacedAt(),
                        v.getStatus() == OrderStatus.CANCELLED))
                .toList();
    }
}