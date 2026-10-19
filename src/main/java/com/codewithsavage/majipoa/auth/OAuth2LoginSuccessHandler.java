package com.codewithsavage.majipoa.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Runs after a successful Google OAuth2 sign-in.
 *
 *  - Normal users  → Angular /auth/callback (which calls GET /api/auth/me and routes by role)
 *  - Admin portal  → Angular /admin/verify  (second step: the admin password)
 */
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Value("${app.oauth2.redirect-uri:http://localhost:4200/auth/callback}")
    private String frontendRedirectUri;

    @Value("${app.oauth2.admin-verify-uri:http://localhost:4200/admin/verify}")
    private String adminVerifyUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        if (authentication instanceof OAuth2AuthenticationToken token
                && AppOidcUserService.ADMIN_REGISTRATION_ID.equals(token.getAuthorizedClientRegistrationId())) {
            // Google step done; password step still pending
            request.getSession().setAttribute(AdminGateInterceptor.VERIFIED, Boolean.FALSE);
            getRedirectStrategy().sendRedirect(request, response, adminVerifyUri);
            return;
        }

        getRedirectStrategy().sendRedirect(request, response, frontendRedirectUri);
    }
}