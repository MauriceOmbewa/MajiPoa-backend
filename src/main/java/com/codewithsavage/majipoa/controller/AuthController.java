package com.codewithsavage.majipoa.controller;

import com.codewithsavage.majipoa.dto.UserResponse;
import com.codewithsavage.majipoa.model.AppUser;
import com.codewithsavage.majipoa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository users;

    @GetMapping("/api/auth/me")
    public UserResponse me(@AuthenticationPrincipal OidcUser principal) {
        AppUser u = users.findByGoogleSub(principal.getSubject()).orElseThrow();
        return new UserResponse(
                u.getId(),
                u.getEmail(),
                u.getName(),
                u.getPictureUrl(),
                u.getRole()
        );
    }
}