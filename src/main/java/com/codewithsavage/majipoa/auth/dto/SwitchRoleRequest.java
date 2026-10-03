package com.codewithsavage.majipoa.auth.dto;

import com.codewithsavage.majipoa.shared.model.Role;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /api/auth/role.
 *
 * Sent when a user wants to upgrade their account to VENDOR or RIDER.
 * A user can only upgrade, never downgrade via this endpoint — going back
 * to the customer view is a frontend-only "active mode" switch, not a DB change.
 *
 * Example body:
 *   { "role": "VENDOR" }
 */
public record SwitchRoleRequest(@NotNull Role role) {}
