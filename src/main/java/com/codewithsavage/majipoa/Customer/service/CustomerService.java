
package com.codewithsavage.majipoa.Customer.service;

import com.codewithsavage.majipoa.Customer.dto.AddressDTO;
import com.codewithsavage.majipoa.Customer.dto.CustomerRequest;
import com.codewithsavage.majipoa.Customer.dto.CustomerResponse;
import com.codewithsavage.majipoa.Customer.model.Address;
import com.codewithsavage.majipoa.Customer.model.Customer;
import com.codewithsavage.majipoa.Customer.repository.CustomerRepository;
import com.codewithsavage.majipoa.shared.dto.LocationRequest;
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
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    /**
     * Gets or initializes the authenticated customer's profile.
     * googleSub comes from the authenticated principal.
     */
    public CustomerResponse getMyProfile(String googleSub) {
        AppUser user = findUserByGoogleSub(googleSub);

        Customer customer = customerRepository.findByUserId(user.getId())
                .orElseGet(() -> createCustomerForUser(user));

        return mapToCustomerResponse(customer);
    }

    private AppUser findUserByGoogleSub(String googleSub) {
        return userRepository.findByGoogleSub(googleSub)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user was not found."
                        )
                );
    }

    /**
     * Creates a customer record when an authenticated user first
     * accesses the customer account.
     */
    private Customer createCustomerForUser(AppUser user) {
        String fullName = user.getName() == null
                ? ""
                : user.getName().trim();

        String[] nameParts = fullName.isBlank()
                ? new String[0]
                : fullName.split("\\s+", 2);

        String firstName = nameParts.length > 0 ? nameParts[0] : "";
        String lastName = nameParts.length > 1 ? nameParts[1] : "";

        Customer customer = Customer.builder()
                .user(user)
                .email(user.getEmail())
                .firstName(firstName)
                .lastName(lastName)
                .userName(fullName)
                .role(user.getRole())
                .build();

        return customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> fetchAllCustomers() {
        return customerRepository.findAll()
                .stream()
                .map(this::mapToCustomerResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<CustomerResponse> fetchCustomer(String customerId) {
        return customerRepository.findById(customerId)
                .map(this::mapToCustomerResponse);
    }

    public boolean updateCustomer(
            String customerId,
            CustomerRequest request) {

        Optional<Customer> optionalCustomer =
                customerRepository.findById(customerId);

        if (optionalCustomer.isEmpty()) {
            return false;
        }

        Customer customer = optionalCustomer.get();

        updateCustomerFromRequest(customer, request);
        customerRepository.save(customer);

        return true;
    }

    /**
     * Updates the authenticated customer's profile.
     */
    public CustomerResponse updateMyProfile(
            String googleSub,
            CustomerRequest request) {

        AppUser user = findUserByGoogleSub(googleSub);

        Customer customer = customerRepository.findByUserId(user.getId())
                .orElseGet(() -> createCustomerForUser(user));

        updateCustomerFromRequest(customer, request);

        Customer savedCustomer = customerRepository.save(customer);

        return mapToCustomerResponse(savedCustomer);
    }

    /**
     * Removes the authenticated customer's saved address.
     */
    public void deleteMyAddress(String googleSub) {
        AppUser user = findUserByGoogleSub(googleSub);

        Customer customer = customerRepository.findByUserId(user.getId())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Customer profile was not found."
                        )
                );

        customer.setAddress(null);
        customerRepository.save(customer);
    }

    /**
     * Applies profile fields and, when supplied, address fields.
     * A null address means "do not change the address".
     */
    private void updateCustomerFromRequest(
            Customer customer,
            CustomerRequest request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "Customer update request cannot be empty."
            );
        }

        customer.setFirstName(request.getFirstName());
        customer.setLastName(request.getLastName());
        customer.setEmail(request.getEmail());
        customer.setUserName(request.getUserName());
        customer.setPhoneNumber(request.getPhoneNumber());

        AddressDTO addressDTO = request.getAddress();

        if (addressDTO != null) {
            Address address = customer.getAddress();

            if (address == null) {
                address = new Address();
            }

            address.setCounty(addressDTO.getCounty());
            address.setSubCounty(addressDTO.getSubCounty());
            address.setArea(addressDTO.getArea());
            address.setStreet(addressDTO.getStreet());
            address.setBuilding(addressDTO.getBuilding());
            address.setLandmark(addressDTO.getLandmark());
            address.setPinLocation(addressDTO.getPinLocation());

            customer.setAddress(address);
        }
    }

    /**
     * Converts the entity into the response returned to Angular.
     */
    private CustomerResponse mapToCustomerResponse(Customer customer) {
        CustomerResponse response = new CustomerResponse();

        response.setCustomerId(customer.getCustomerId());
        response.setFirstName(customer.getFirstName());
        response.setLastName(customer.getLastName());
        response.setUserName(customer.getUserName());
        response.setPhoneNumber(customer.getPhoneNumber());
        response.setRole(customer.getRole());

        AppUser user = customer.getUser();

        if (user != null) {
            response.setPictureUrl(user.getPictureUrl());

            if (customer.getEmail() != null
                    && !customer.getEmail().isBlank()) {
                response.setEmail(customer.getEmail());
            } else {
                response.setEmail(user.getEmail());
            }
        } else {
            response.setEmail(customer.getEmail());
        }

        Address address = customer.getAddress();

        if (address != null) {
            AddressDTO addressDTO = new AddressDTO();

            addressDTO.setCounty(address.getCounty());
            addressDTO.setSubCounty(address.getSubCounty());
            addressDTO.setArea(address.getArea());
            addressDTO.setStreet(address.getStreet());
            addressDTO.setBuilding(address.getBuilding());
            addressDTO.setLandmark(address.getLandmark());
            addressDTO.setPinLocation(address.getPinLocation());

            response.setAddress(addressDTO);
        } else {
            response.setAddress(null);
        }

        return response;
    }

    public Customer getCustomerByGoogleSub(String googleSub) {
        AppUser user = findUserByGoogleSub(googleSub);

        return customerRepository.findByUserId(user.getId())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Customer profile was not found."
                        )
                );
    }

    /**
     * Saves the coordinates the browser sent after the customer allowed location access.
     * Creates an Address if the customer doesn't have one yet.
     */
    @Transactional
    public void updateLocation(String googleSub, LocationRequest req) {

        AppUser user = userRepository.findByGoogleSub(googleSub)
                .orElseThrow(() ->
                        new IllegalStateException("User not found.")
                );

        Customer customer = customerRepository
                .findByUserId(user.getId())
                .orElseGet(() -> createCustomerForUser(user));

        Address address = customer.getAddress();
        if (address == null) {
            address = new Address();
            customer.setAddress(address);   // Customer cascades ALL, so it is saved with the customer
        }
        address.setLatitude(req.latitude());
        address.setLongitude(req.longitude());

        customerRepository.save(customer);
    }
}
