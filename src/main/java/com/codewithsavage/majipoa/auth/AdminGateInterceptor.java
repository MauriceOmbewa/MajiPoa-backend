package com.codewithsavage.majipoa.auth;

import com.codewithsavage.majipoa.shared.model.Role;
import com.codewithsavage.majipoa.shared.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Guards /api/admin/**. A request only passes when ALL are true:
 *   1. The session was created by the admin Google login (google-admin registration)
 *   2. That account is ADMIN in the database
 *   3. The admin password has been verified in this session
 */
@Component
@RequiredArgsConstructor
public class AdminGateInterceptor implements HandlerInterceptor {

    public static final String VERIFIED = "ADMIN_PASSWORD_VERIFIED";

    private final UserRepository users;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = req.getSession(false);

        boolean viaAdminLogin = auth instanceof OAuth2AuthenticationToken t
                && AppOidcUserService.ADMIN_REGISTRATION_ID.equals(t.getAuthorizedClientRegistrationId());

        boolean isAdmin = viaAdminLogin
                && auth.getPrincipal() instanceof OidcUser oidc
                && users.findByGoogleSub(oidc.getSubject())
                        .map(u -> u.getRole() == Role.ADMIN).orElse(false);

        boolean verified = session != null && Boolean.TRUE.equals(session.getAttribute(VERIFIED));

        if (isAdmin && verified) return true;

        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"admin_verification_required\"}");
        return false;
    }
}