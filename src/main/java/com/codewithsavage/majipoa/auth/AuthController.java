package com.codewithsavage.majipoa.auth;

import com.codewithsavage.majipoa.auth.dto.SwitchRoleRequest;
import com.codewithsavage.majipoa.auth.dto.UserResponse;
import com.codewithsavage.majipoa.shared.model.AppUser;
import com.codewithsavage.majipoa.shared.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

/**
 * Handles authentication endpoints for the Angular frontend.
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  Endpoint              │ What it does                           │
 * ├─────────────────────────────────────────────────────────────────┤
 * │  GET  /api/auth/me     │ Returns the logged-in user's profile   │
 * │  POST /api/auth/logout │ Ends the server-side session           │
 * │  POST /api/auth/role   │ Upgrades the user's role (→ VENDOR /   │
 * │                        │ RIDER) and returns updated profile     │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * The Google sign-in flow itself is handled entirely by Spring Security —
 * there are no endpoints here for it. The flow is:
 *   Frontend → GET /oauth2/authorization/google (Spring Security)
 *           → Google sign-in page
 *           → GET /login/oauth2/code/google (Spring Security)
 *           → AppOidcUserService (upserts user)
 *           → OAuth2LoginSuccessHandler (redirects to /auth/callback on frontend)
 *           → Frontend calls GET /api/auth/me here to get the user profile
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository users;
    private final AuthService    authService;

    // ── Helper ───────────────────────────────────────────────────────────────

    private AppUser currentUser(OidcUser principal) {
        return users.findByGoogleSub(principal.getSubject())
                .orElseThrow(() -> new IllegalStateException("User not found in DB"));
    }

    // ── Endpoints ────────────────────────────────────────────────────────────

    /**
     * GET /api/auth/me
     *
     * Called by the Angular /auth/callback page after a Google sign-in,
     * and on every app startup to restore session from the server-side cookie.
     */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal OidcUser principal) {
        return authService.toResponse(currentUser(principal));
    }

    /**
     * POST /api/auth/logout
     *
     * Invalidates the server-side session. After this, the user's cookies
     * are worthless and all subsequent API calls will return 401.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/auth/role
     *
     * Permanently upgrades the user's role to VENDOR or RIDER.
     * Called when the user accepts the "Join as vendor / Become a rider" confirmation.
     *
     * This is a one-way operation — once a user is a VENDOR they cannot be
     * demoted back to CUSTOMER via API (they just switch their active view in the frontend).
     *
     * Body: { "role": "VENDOR" }  or  { "role": "RIDER" }
     */
    @PostMapping("/role")
    public UserResponse switchRole(@AuthenticationPrincipal OidcUser principal,
                                   @Valid @RequestBody SwitchRoleRequest body) {
        return authService.switchRole(currentUser(principal), body);
    }
}
