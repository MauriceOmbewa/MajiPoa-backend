package com.codewithsavage.majipoa.Customer.service;

import com.codewithsavage.majipoa.Customer.dto.AddressDTO;
import com.codewithsavage.majipoa.Customer.dto.CustomerRequest;
import com.codewithsavage.majipoa.Customer.dto.CustomerResponse;
import com.codewithsavage.majipoa.Customer.model.Address;
import com.codewithsavage.majipoa.Customer.model.Customer;
import com.codewithsavage.majipoa.Customer.repository.CustomerRepository;
import com.codewithsavage.majipoa.shared.model.AppUser;
import com.codewithsavage.majipoa.shared.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    /**
     * Gets the currently authenticated customer's profile.
     * <p>
     * The googleSub comes from the authenticated Google user.
     */
    @Transactional
    public CustomerResponse getMyProfile(String googleSub) {

        AppUser user = userRepository.findByGoogleSub(googleSub)
                .orElseThrow(() ->
                        new IllegalStateException("Authenticated user not found.")
                );

        Customer customer = customerRepository.findByUserId(user.getId())
                .orElseGet(() -> createCustomerForUser(user));

        return mapToCustomerResponse(customer);
    }

    /**
     * Creates the Customer record the first time
     * an authenticated user accesses the Customer side.
     */
    private Customer createCustomerForUser(AppUser user) {

        Customer customer = Customer.builder()
                .user(user)
                .email(user.getEmail())
                .role(user.getRole())
                .build();

        return customerRepository.save(customer);
    }

    public List<CustomerResponse> fetchAllCustomers() {
        return customerRepository.findAll().stream()
                .map(this::mapToCustomerResponse)
                .collect(Collectors.toList());
    }

    public Optional<CustomerResponse> fetchCustomer(String customerId) {
        return customerRepository.findById(String.valueOf(customerId))
                .map(this::mapToCustomerResponse);
    }

    public boolean updateCustomer(String customerId, CustomerRequest updatedCustomerRequest) {
        return customerRepository.findById(String.valueOf(customerId))
                .map(existingCustomer ->{
                    updateCustomerFromRequest(existingCustomer,updatedCustomerRequest);
                    customerRepository.save(existingCustomer);
                    return true;
                }).orElse(false);
    }

    private CustomerResponse mapToCustomerResponse(Customer customer) {
        CustomerResponse response = new CustomerResponse();
        response.setCustomerId(customer.getCustomerId());
        response.setEmail(customer.getEmail());
        response.setFirstName(customer.getFirstName());
        response.setLastName(customer.getLastName());
        //response.setPassword(customer.getPassword());
        response.setUserName(customer.getUserName());
        response.setPhoneNumber(customer.getPhoneNumber());
        response.setRole(customer.getRole());

        if (customer.getAddress() != null) {
            AddressDTO addressDTO = new AddressDTO();
            addressDTO.setCounty(customer.getAddress().getCounty());
            addressDTO.setSubCounty(customer.getAddress().getSubCounty());
            addressDTO.setArea(customer.getAddress().getArea());
            addressDTO.setStreet(customer.getAddress().getStreet());
            addressDTO.setBuilding(customer.getAddress().getBuilding());
            addressDTO.setLandmark(customer.getAddress().getLandmark());
            addressDTO.setPinLocation(customer.getAddress().getPinLocation());
            response.setAddress(addressDTO);
        }
        return response;
    }

    /**
     * Converts the Customer entity into the response
     * that Angular receives.
     */
//    private CustomerResponse toResponse(Customer customer) {
//
//        AppUser user = customer.getUser();
//
//        return new CustomerResponse(
//                customer.getcustomerId(),
//                user.getId(),
//                user.getEmail(),
//                user.getName(),
//                user.getPictureUrl(),
//                user.getRole(),
//                customer.getPhoneNumber()
//        );
//    }


    private void updateCustomerFromRequest(Customer customer, CustomerRequest customerRequest) {
        customer.setFirstName(customerRequest.getFirstName());
        customer.setLastName(customerRequest.getLastName());
        customer.setEmail(customerRequest.getEmail());
        customer.setUserName(customerRequest.getUserName());
        //customer.setPassword(customerRequest.getPassword());
        customer.setPhoneNumber(customerRequest.getPhoneNumber());
        if ( customerRequest.getAddress() != null ) {
            Address address = customer.getAddress();
            if (address == null) {
                address = new Address();
            }
            address.setCounty(customerRequest.getAddress().getCounty());
            address.setSubCounty(customerRequest.getAddress().getSubCounty());
            address.setArea(customerRequest.getAddress().getArea());
            address.setStreet(customerRequest.getAddress().getStreet());
            address.setBuilding(customerRequest.getAddress().getBuilding());
            address.setLandmark(customerRequest.getAddress().getLandmark());
            customer.setAddress(address);
        }
    }

    public Customer getCustomerByGoogleSub(String googleSub) {

        AppUser user = userRepository.findByGoogleSub(googleSub)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        return customerRepository.findByUserId(user.getId())
                .orElseThrow(() ->
                        new RuntimeException("Customer profile not found"));
    }

    @Transactional
    public CustomerResponse updateMyProfile(
            String googleSub,
            CustomerRequest request) {

        AppUser user = userRepository.findByGoogleSub(googleSub)
                .orElseThrow(() ->
                        new IllegalStateException("User not found.")
                );

        Customer customer = customerRepository
                .findByUserId(user.getId())
                .orElseGet(() -> createCustomerForUser(user));

        updateCustomerFromRequest(customer, request);

        Customer savedCustomer = customerRepository.save(customer);

        return mapToCustomerResponse(savedCustomer);
    }
}