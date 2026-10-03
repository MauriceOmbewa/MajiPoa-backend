package com.codewithsavage.majipoa.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Runs after a successful Google OAuth2 sign-in.
 *
 * Instead of redirecting to a backend page (which the user never sees directly),
 * we redirect to the Angular frontend's /auth/callback route.
 *
 * The Angular component at /auth/callback then calls GET /api/auth/me to find
 * out who just signed in and routes them to the correct portal (customer/vendor/rider).
 */
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Value("${app.oauth2.redirect-uri:http://localhost:4200/auth/callback}")
    private String frontendRedirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        getRedirectStrategy().sendRedirect(request, response, frontendRedirectUri);
    }
}
