package com.codewithsavage.majipoa.shared.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * The single user record for every person on the platform, regardless of role.
 *
 * How it works:
 *  1. A new user signs in with Google for the first time.
 *  2. AppOidcUserService (in the auth package) creates this record automatically
 *     and assigns the CUSTOMER role by default.
 *  3. If the user later asks to become a VENDOR or RIDER, AuthService.switchRole()
 *     updates the role field here, and the frontend unlocks the corresponding portal.
 *
 * Fields:
 *  googleSub   – Google's unique and stable user identifier (the "sub" claim in the JWT).
 *                We use this as the primary lookup key, not the email, because emails can change.
 *  role        – The highest capability level granted to this user (see Role enum).
 *  createdAt   – When the account was first created (set once, never updated).
 *  lastLoginAt – Updated every time the user completes a Google sign-in.
 */
@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String googleSub;

    @Column(unique = true, nullable = false)
    private String email;

    private String name;
    private String pictureUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.CUSTOMER;

    private Instant createdAt   = Instant.now();
    private Instant lastLoginAt;
}
