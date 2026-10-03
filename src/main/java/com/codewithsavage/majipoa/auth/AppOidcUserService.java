package com.codewithsavage.majipoa.auth;

import com.codewithsavage.majipoa.shared.model.AppUser;
import com.codewithsavage.majipoa.shared.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Called by Spring Security after Google returns a successful OIDC token.
 *
 * What happens here:
 *   1. We call super.loadUser() to let Spring parse Google's ID token and UserInfo.
 *   2. We look up (or create) the user in our own database by googleSub.
 *   3. We refresh the user's profile fields (email, name, picture) from Google.
 *   4. We inject the user's MajiSafi role into Spring Security's authority list
 *      so that @PreAuthorize / hasRole() checks work correctly.
 *
 * This is the "upsert on login" pattern — new users are auto-registered the
 * first time they sign in with Google. No separate registration step needed.
 */
@Service
@RequiredArgsConstructor
public class AppOidcUserService extends OidcUserService {

    private final UserRepository users;

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        // 1. Let Spring Security do the standard OIDC work
        OidcUser oidc = super.loadUser(request);

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

        // 4. Add our app role as a Spring Security authority (e.g. ROLE_CUSTOMER)
        Set<GrantedAuthority> authorities = new HashSet<>(oidc.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

        return new DefaultOidcUser(authorities, oidc.getIdToken(), oidc.getUserInfo());
    }
}
