package com.codewithsavage.majipoa.security;

import com.codewithsavage.majipoa.model.AppUser;
import com.codewithsavage.majipoa.repository.UserRepository;
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

@Service
@RequiredArgsConstructor
public class AppOidcUserService extends OidcUserService {

    private final UserRepository users;

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        OidcUser oidc = super.loadUser(request);

        AppUser user = users.findByGoogleSub(oidc.getSubject())
                .orElseGet(AppUser::new);

        user.setGoogleSub(oidc.getSubject());
        user.setEmail(oidc.getEmail());
        user.setName(oidc.getFullName());
        user.setPictureUrl(oidc.getPicture());
        user.setLastLoginAt(Instant.now());
        users.save(user);

        Set<GrantedAuthority> authorities = new HashSet<>(oidc.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole()));

        return new DefaultOidcUser(authorities, oidc.getIdToken(), oidc.getUserInfo());
    }
}