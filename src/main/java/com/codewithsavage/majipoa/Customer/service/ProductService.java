package com.codewithsavage.majipoa.Customer.service;


import com.codewithsavage.majipoa.Customer.dto.ProductResponse;
import com.codewithsavage.majipoa.Customer.model.Product;
import com.codewithsavage.majipoa.Customer.repository.CustomerProductRepository;
import com.codewithsavage.majipoa.Customer.dto.ProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final CustomerProductRepository productRepository;

    public ProductResponse createProduct(ProductRequest productRequest) {
        Product product = new Product();
        updateProductFromRequest(product, productRequest);
        Product savedProduct = productRepository.save(product);
        return mapToProductResponse(savedProduct);

    }

    private ProductResponse mapToProductResponse(Product savedProduct) {
        ProductResponse response = new ProductResponse();
        response.setProductId(savedProduct.getProductId());
        response.setName(savedProduct.getName());
        response.setActive(savedProduct.getActive());
        response.setPrice(savedProduct.getPrice());
        response.setCategory(savedProduct.getCategory());
        response.setDescription(savedProduct.getDescription());
        //response.setColor(savedProduct.getColor());
        response.setStatus(savedProduct.getStatus());
        response.setSize(savedProduct.getSize());
        response.setImageUrl(savedProduct.getImageUrl());
        response.setStockQuantity(savedProduct.getStockQuantity());
        return response;
    }

    private void updateProductFromRequest(Product product, ProductRequest productRequest) {
        product.setName(productRequest.getName());
//        product.setActive(productRequest.getActive());
        product.setPrice(productRequest.getPrice());
        product.setCategory(productRequest.getCategory());
        product.setDescription(productRequest.getDescription());
        //product.setColor(productRequest.getColor());
        product.setStatus(productRequest.getStatus());
        product.setSize(productRequest.getSize());
        product.setImageUrl(productRequest.getImageUrl());
        product.setStockQuantity(productRequest.getStockQuantity());
    }

    public Optional<ProductResponse> updateProduct(String productId, ProductRequest productRequest) {
        return productRepository.findById(productId)
                .map(existingProduct -> {
                    updateProductFromRequest(existingProduct, productRequest);
                    Product savedProduct = productRepository.save(existingProduct);
                    return mapToProductResponse(savedProduct);
                });
    }

    public List<ProductResponse> getAllProducts() {
        return productRepository.findByActiveTrue().stream()
                .map(this::mapToProductResponse)
                .collect(Collectors.toList());

    }

    public boolean deleteProduct(String productId) {
        return productRepository.findById(productId)
                .map(product -> {
                    product.setActive(false);
                    productRepository.save(product);
                    return true;
                } ).orElse(false);

    }

    public List<ProductResponse> searchProducts(String keyword) {
        return productRepository.searchProducts(keyword).stream()
                .map(this::mapToProductResponse)
                .collect(Collectors.toList());
    }
}
