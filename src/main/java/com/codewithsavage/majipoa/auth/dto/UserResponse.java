package com.codewithsavage.majipoa.auth.dto;

import com.codewithsavage.majipoa.shared.model.Role;

/**
 * The JSON payload returned by GET /api/auth/me.
 *
 * Contains everything the Angular frontend needs to know about the current user:
 *  - id / email / name / pictureUrl : display info for the navbar
 *  - role : determines which portals the user is allowed to switch into
 *
 * NOTE: googleSub is deliberately excluded — it's an internal identifier
 * that the frontend never needs to see.
 */
public record UserResponse(
        Long   id,
        String email,
        String name,
        String pictureUrl,
        Role   role
) {}
