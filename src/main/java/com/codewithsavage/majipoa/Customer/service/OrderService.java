package com.codewithsavage.majipoa.Customer.service;

import com.codewithsavage.majipoa.Customer.dto.CartItemRequest;
import com.codewithsavage.majipoa.Customer.dto.OrderItemDTO;
import com.codewithsavage.majipoa.Customer.dto.OrderResponse;
import com.codewithsavage.majipoa.Customer.model.*;
import com.codewithsavage.majipoa.Customer.repository.OrderRepository;
import com.codewithsavage.majipoa.Customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final CartItemService  cartItemService;
    private final CustomerRepository  customerRepository;
    private final OrderRepository orderRepository;

    @Transactional
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
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        //create order
        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);
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
        Order savedOrder = orderRepository.saveAndFlush(order);

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

    @Transactional(readOnly = true)
    public List<OrderResponse> getCustomerOrders(String customerId) {
        return orderRepository
                .findByCustomer_CustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(this::mapToOrderResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<OrderResponse> getCustomerOrder(
            String orderId,
            String customerId
    ) {
        return orderRepository
                .findByOrderIdAndCustomer_CustomerId(orderId, customerId)
                .map(this::mapToOrderResponse);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll()
                .stream()
                .map(this::mapToOrderResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getActiveOrders() {
        // Replace these with the actual terminal statuses in your enum.
        List<OrderStatus> inactiveStatuses = List.of(
                OrderStatus.DELIVERED,
                OrderStatus.CANCELLED



        );

        return orderRepository
                .findByStatusNotInOrderByCreatedAtDesc(inactiveStatuses)
                .stream()
                .map(this::mapToOrderResponse)
                .toList();
    }

    @Transactional
    public Optional<OrderResponse> updateOrderStatus(
            String orderId,
            OrderStatus newStatus
    ) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    order.setStatus(newStatus);

                    Order savedOrder = orderRepository.save(order);

                    return mapToOrderResponse(savedOrder);
                });
    }

    @Transactional
    public Optional<OrderResponse> confirmSuccessfulPayment(String orderId) {
        return orderRepository.findById(orderId)
                .map(order -> {
                    // Make repeated payment callbacks safe.
                    if (order.getStatus() == OrderStatus.CONFIRMED) {
                        return mapToOrderResponse(order);
                    }

                    if (order.getStatus() != OrderStatus.PENDING) {
                        throw new IllegalStateException(
                                "This order cannot be confirmed from status: "
                                        + order.getStatus()
                        );
                    }

                    order.setStatus(OrderStatus.CONFIRMED);

                    Order savedOrder = orderRepository.save(order);
                    return mapToOrderResponse(savedOrder);
                });
    }

    @Transactional
    public Optional<com.codewithsavage.majipoa.Customer.dto.CartResponse> reorder(
            String orderId,
            String customerId
    ) {
        Optional<Order> previousOrder =
                orderRepository.findByOrderIdAndCustomer_CustomerId(
                        orderId,
                        customerId
                );

        if (previousOrder.isEmpty()) {
            return Optional.empty();
        }

        Order order = previousOrder.get();

        for (OrderItem item : order.getItems()) {
            CartItemRequest request = new CartItemRequest(
                    item.getProduct().getProductId(),
                    item.getQuantity()
            );

            boolean added = cartItemService.addToCart(customerId, request);

            if (!added) {
                throw new IllegalStateException(
                        "Unable to reorder product: "
                                + item.getProduct().getName()
                );
            }
        }

        return Optional.of(cartItemService.getCartResponse(customerId));
    }
}

