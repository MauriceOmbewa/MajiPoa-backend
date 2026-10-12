package com.codewithsavage.majipoa.Customer.service;

import com.codewithsavage.majipoa.Customer.dto.OrderItemDTO;
import com.codewithsavage.majipoa.Customer.dto.OrderResponse;
import com.codewithsavage.majipoa.Customer.model.*;
import com.codewithsavage.majipoa.Customer.repository.OrderRepository;
import com.codewithsavage.majipoa.Customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final CartItemService  cartItemService;
    private final CustomerRepository  customerRepository;
    private final OrderRepository orderRepository;

    public Optional<OrderResponse> createOrder(String customerId) {
        //validate for cart items
        List<CartItem> cartItems = cartItemService.getCartItem(customerId);
        if (cartItems.isEmpty()) {
            return Optional.empty();

        }

        //validate for user
        Optional<Customer> userOptional = customerRepository.findById((customerId));
        if ( userOptional.isEmpty()) {
            return Optional.empty();
        }

        Customer customer = userOptional.get();

        //calculate total price
        BigDecimal totalPrice = cartItems.stream()
                .map(CartItem::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        //create order
        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.CONFIRMED);
        order.setTotalAmount(totalPrice);
        List<OrderItem> orderItems = cartItems.stream()
                .map(item -> new OrderItem(
                        null,
                        item.getProduct(),
                        item.getQuantity(),
                        item.getPrice(),
                        order
                ))
                .toList();
        order.setItems(orderItems);
        Order savedOrder = orderRepository.save(order);

        //clear the cart
        cartItemService.clearCart(customerId);

        return Optional.of(mapToOrderResponse(savedOrder));
    }

    private OrderResponse mapToOrderResponse(Order order) {
        return new OrderResponse(
                order.getOrderId(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getItems().stream()
                        .map(orderItem -> new OrderItemDTO(
                                orderItem.getOrderItemId(),
                                orderItem.getProduct().getProductId(),
                                orderItem.getQuantity(),
                                orderItem.getPrice(),
                                orderItem.getPrice().multiply(new BigDecimal(orderItem.getQuantity()))
                        ))
                        .toList(),
                order.getCreatedAt()




        );
    }
}

