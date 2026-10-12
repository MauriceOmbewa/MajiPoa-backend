package com.codewithsavage.majipoa.Customer.service;

import com.codewithsavage.majipoa.Customer.dto.CartItemRequest;
import com.codewithsavage.majipoa.Customer.model.CartItem;
import com.codewithsavage.majipoa.Customer.model.Customer;
import com.codewithsavage.majipoa.Customer.model.Product;
import com.codewithsavage.majipoa.Customer.repository.CartItemRepository;
import com.codewithsavage.majipoa.Customer.repository.CustomerRepository;
import com.codewithsavage.majipoa.Customer.repository.CustomerProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.codewithsavage.majipoa.Customer.dto.CartItemResponse;
import com.codewithsavage.majipoa.Customer.dto.CartResponse;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartItemService {
    private final CustomerProductRepository productRepository;
    private final CartItemRepository cartItemRepository;
    private final CustomerRepository customerRepository;
    public boolean addToCart(String customerId, CartItemRequest request) {
        // Look for product
        Optional<Product> productOpt = productRepository.findById(request.getProductId());
        if (productOpt.isEmpty())
            return false;
        Product product = productOpt.get();
        if (product.getStockQuantity() < request.getQuantity())
            return false;
        Optional<Customer> customerOpt = customerRepository.findById((customerId));
        if (customerOpt.isEmpty())
            return false;

        Customer customer = customerOpt.get();

        CartItem existingCartitem = cartItemRepository.findByCustomerAndProduct(customer, product);
        if ( existingCartitem != null){
            //Update the quantity
            existingCartitem.setQuantity(existingCartitem.getQuantity() + request.getQuantity());
            existingCartitem.setPrice(product.getPrice().multiply(BigDecimal.valueOf(existingCartitem.getQuantity())));
            cartItemRepository.save(existingCartitem);
        } else {
            //create new cart item
            CartItem cartItem = new CartItem();
            cartItem.setCustomer(customer);
            cartItem.setProduct(product);
            cartItem.setQuantity(request.getQuantity());
            cartItem.setPrice(product.getPrice().multiply(BigDecimal.valueOf(request.getQuantity())));
            cartItemRepository.save(cartItem);
        }
        return true;


    }

    public boolean deleteItemFromCart(String customerId, String productId) {
        Optional<Product> productOpt = productRepository.findById(productId);
        Optional<Customer> userOpt = customerRepository.findById(customerId);
        if ( productOpt.isPresent() && userOpt.isPresent()){
            cartItemRepository.deleteByCustomerAndProduct(userOpt.get(), productOpt.get());
            return true;
        }
        return false;
    }

    public List<CartItem> getCartItem(String customerId) {
        return customerRepository.findById(customerId)
                .map(cartItemRepository::findByCustomer)
                .orElseGet(List::of);
    }

    public void clearCart(String customerId) {
        customerRepository.findById(customerId).ifPresent(
                cartItemRepository::deleteByCustomer
        );
    }

    @Transactional(readOnly = true)
    public CartResponse getCartResponse(String customerId) {

        List<CartItem> cartItems = getCartItem(customerId);

        List<CartItemResponse> items = cartItems.stream()
                .map(item -> {
                    BigDecimal subtotal = item.getPrice()
                            .multiply(BigDecimal.valueOf(item.getQuantity()));

                    return new CartItemResponse(
                            item.getCartItemId(),
                            item.getProduct().getProductId(),
                            item.getProduct().getName(),
                            item.getProduct().getImageUrl(),
                            item.getQuantity(),
                            item.getPrice(),
                            subtotal
                    );
                })
                .toList();

        int totalQuantity = cartItems.stream()
                .mapToInt(CartItem::getQuantity)
                .sum();

        BigDecimal totalAmount = items.stream()
                .map(CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(
                items,
                totalQuantity,
                totalAmount
        );
    }
}
