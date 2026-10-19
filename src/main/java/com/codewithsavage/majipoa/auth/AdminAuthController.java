package com.codewithsavage.majipoa.auth;

import com.codewithsavage.majipoa.shared.model.Role;
import com.codewithsavage.majipoa.shared.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Second step of admin login: the admin password.
 *
 *   POST /api/admin-auth/verify-password  { "password": "..." }
 *   GET  /api/admin-auth/status           → { "verified": true|false }
 *
 * Lives outside /api/admin/** on purpose so the gate doesn't block the verification itself.
 */
@RestController
@RequestMapping("/api/admin-auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private static final int MAX_ATTEMPTS = 5;
    private static final String ATTEMPTS = "ADMIN_PW_ATTEMPTS";

    private final UserRepository users;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Value("${app.admin.password-hash}")
    private String passwordHash;

    public record VerifyRequest(@NotBlank String password) {}

    @PostMapping("/verify-password")
    public ResponseEntity<Map<String, Object>> verify(Authentication auth,
                                                      @Valid @RequestBody VerifyRequest body,
                                                      HttpSession session) {
        boolean viaAdminLogin = auth instanceof OAuth2AuthenticationToken t
                && AppOidcUserService.ADMIN_REGISTRATION_ID.equals(t.getAuthorizedClientRegistrationId());

        boolean isAdmin = viaAdminLogin
                && auth.getPrincipal() instanceof OidcUser oidc
                && users.findByGoogleSub(oidc.getSubject())
                        .map(u -> u.getRole() == Role.ADMIN).orElse(false);

        if (!isAdmin) {
            return ResponseEntity.status(403).body(Map.of("error", "not_admin"));
        }

        int attempts = (int) Optional_get(session);
        if (attempts >= MAX_ATTEMPTS) {
            session.invalidate();   // force a fresh Google sign-in
            return ResponseEntity.status(429).body(Map.of("error", "too_many_attempts"));
        }

        if (!encoder.matches(body.password(), passwordHash)) {
            session.setAttribute(ATTEMPTS, attempts + 1);
            return ResponseEntity.status(401).body(Map.of("error", "wrong_password"));
        }

        session.setAttribute(AdminGateInterceptor.VERIFIED, Boolean.TRUE);
        session.removeAttribute(ATTEMPTS);
        return ResponseEntity.ok(Map.of("verified", true));
    }

    @GetMapping("/status")
    public Map<String, Boolean> status(HttpSession session) {
        return Map.of("verified", Boolean.TRUE.equals(session.getAttribute(AdminGateInterceptor.VERIFIED)));
    }

    private static Object Optional_get(HttpSession session) {
        Object v = session.getAttribute(ATTEMPTS);
        return v == null ? 0 : v;
    }
}