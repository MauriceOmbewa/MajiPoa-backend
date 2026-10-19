package com.codewithsavage.majipoa.auth;

import com.codewithsavage.majipoa.Customer.model.Customer;
import com.codewithsavage.majipoa.Customer.repository.CustomerRepository;
import com.codewithsavage.majipoa.shared.model.AppUser;
import com.codewithsavage.majipoa.shared.model.Role;
import com.codewithsavage.majipoa.shared.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Called by Spring Security after Google returns a successful OIDC token.
 *
 * Two registrations share this service:
 *   - "google"       : normal users. Upsert on login, default role CUSTOMER.
 *   - "google-admin" : admin portal. NEVER auto-creates ordinary users. The account must
 *                      already be ADMIN in the DB, or its email must be in app.admin.emails
 *                      (in which case it is created/promoted to ADMIN).
 */
@Service
@RequiredArgsConstructor
public class AppOidcUserService extends OidcUserService {

    public static final String ADMIN_REGISTRATION_ID = "google-admin";

    private final UserRepository users;
    private final CustomerRepository customers;

    @Value("${app.admin.emails:}")
    private String adminEmailsCsv;

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest request) {
        // 1. Let Spring Security do the standard OIDC work
        OidcUser oidc = super.loadUser(request);

        // Admin portal login takes a separate, stricter path
        if (ADMIN_REGISTRATION_ID.equals(request.getClientRegistration().getRegistrationId())) {
            return loadAdmin(oidc);
        }

        // 2. Find existing user or create a new one
        AppUser user = users.findByGoogleSub(oidc.getSubject())
                .orElseGet(AppUser::new);

        // 3. Sync profile fields from Google
        user.setGoogleSub(oidc.getSubject());
        user.setEmail(oidc.getEmail());
        user.setName(oidc.getFullName());
        user.setPictureUrl(oidc.getPicture());
        user.setLastLoginAt(Instant.now());
        users.save(user);

        // Keep the customer domain record linked to this platform identity.
        Customer customer = customers.findByUserId(user.getId())
                .orElseGet(() -> Customer.builder().user(user).build());
        customer.setUser(user);
        customer.setEmail(user.getEmail());
        customer.setRole(user.getRole());
        if (customer.getFirstName() == null || customer.getFirstName().isBlank()) {
            String[] nameParts = splitName(user.getName());
            customer.setFirstName(nameParts[0]);
            customer.setLastName(nameParts[1]);
        }
        if (customer.getUserName() == null || customer.getUserName().isBlank()) {
            customer.setUserName(user.getEmail());
        }
        customers.save(customer);

        // 4. Add our app role as a Spring Security authority (e.g. ROLE_CUSTOMER)
        Set<GrantedAuthority> authorities = new HashSet<>(oidc.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

        return new DefaultOidcUser(authorities, oidc.getIdToken(), oidc.getUserInfo());
    }

    /** Admin Google login: only accounts that are (or are allowlisted as) ADMIN get through. */
    private OidcUser loadAdmin(OidcUser oidc) {
        System.out.println("ADMIN LOGIN email=[" + oidc.getEmail() + "] verified=" + oidc.getEmailVerified()
        + " allowlist=[" + adminEmailsCsv + "]");
        AppUser user = users.findByGoogleSub(oidc.getSubject()).orElse(null);
        boolean allowlisted = Boolean.TRUE.equals(oidc.getEmailVerified())
                && isAllowlisted(oidc.getEmail());

        if ((user == null || user.getRole() != Role.ADMIN) && !allowlisted) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("not_admin"), "This account is not an administrator.");
        }

        if (user == null) {
            user = new AppUser();
            user.setGoogleSub(oidc.getSubject());
        }
        user.setEmail(oidc.getEmail());
        user.setName(oidc.getFullName());
        user.setPictureUrl(oidc.getPicture());
        user.setRole(Role.ADMIN);
        user.setLastLoginAt(Instant.now());
        users.save(user);

        Set<GrantedAuthority> authorities = new HashSet<>(oidc.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        return new DefaultOidcUser(authorities, oidc.getIdToken(), oidc.getUserInfo());
    }

    private boolean isAllowlisted(String email) {
        if (email == null || adminEmailsCsv == null || adminEmailsCsv.isBlank()) return false;
        return Arrays.stream(adminEmailsCsv.split(","))
                .map(String::trim)
                .anyMatch(e -> e.equalsIgnoreCase(email));
    }

    private String[] splitName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[]{null, null};
        }
        String normalized = fullName.trim();
        int separator = normalized.indexOf(' ');
        return separator < 0
                ? new String[]{normalized, null}
                : new String[]{normalized.substring(0, separator), normalized.substring(separator + 1).trim()};
    }
}