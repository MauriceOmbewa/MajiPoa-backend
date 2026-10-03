package com.codewithsavage.majipoa.auth;

import com.codewithsavage.majipoa.auth.dto.SwitchRoleRequest;
import com.codewithsavage.majipoa.auth.dto.UserResponse;
import com.codewithsavage.majipoa.shared.model.AppUser;
import com.codewithsavage.majipoa.shared.model.Role;
import com.codewithsavage.majipoa.shared.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles authentication-related business logic.
 *
 * Currently responsible for:
 *   - Building the UserResponse DTO from an AppUser entity
 *   - Role upgrading (CUSTOMER → VENDOR or CUSTOMER → RIDER)
 *
 * Role upgrade rules:
 *   - A user can request to become a VENDOR or RIDER.
 *   - In a real system this would trigger a review/onboarding flow.
 *     Here we grant it immediately for demo purposes.
 *   - Roles only ever increase in capability (CUSTOMER < VENDOR / RIDER < ADMIN).
 *   - ADMIN can only be set directly in the database.
 *   - Trying to downgrade (e.g. VENDOR → CUSTOMER) is rejected with an exception.
 *     Switching back to customer VIEW is purely a frontend concept (RoleService.activeMode).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;

    /** Converts an AppUser entity into the JSON response the frontend receives. */
    public UserResponse toResponse(AppUser u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getName(), u.getPictureUrl(), u.getRole());
    }

    /**
     * Upgrades the user's role in the database.
     *
     * The frontend calls this when the user clicks "Join as vendor" or "Become a rider"
     * and confirms they want to activate that mode on their account permanently.
     *
     * @throws IllegalStateException if the requested role is not an upgrade
     */
    @Transactional
    public UserResponse switchRole(AppUser user, SwitchRoleRequest req) {
        Role requested = req.role();

        // ADMIN can only be set via direct DB access — never via this endpoint
        if (requested == Role.ADMIN) {
            throw new IllegalStateException("Cannot self-assign the ADMIN role.");
        }

        // Downgrading is not allowed via API (frontend handles customer-view switching)
        if (requested == Role.CUSTOMER && user.getRole() != Role.CUSTOMER) {
            throw new IllegalStateException(
                "To switch back to customer view, use the mode switcher in the app — " +
                "your account role is not changed."
            );
        }

        user.setRole(requested);
        AppUser saved = users.save(user);
        return toResponse(saved);
    }
}
