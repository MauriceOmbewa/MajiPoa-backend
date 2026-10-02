package com.codewithsavage.majipoa.dto;

import com.codewithsavage.majipoa.model.Role;

public record UserResponse(
        Long id,
        String email,
        String name,
        String pictureUrl,
        Role role
) {}