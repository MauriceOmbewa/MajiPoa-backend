package com.codewithsavage.majipoa.shared.model;

/**
 * The three roles a MajiSafi user can hold.
 *
 * IMPORTANT: a user's database role is their HIGHEST allowed capability.
 * Separately, the frontend tracks an "active mode" (customer / vendor / rider)
 * so someone with ROLE_VENDOR can still browse as a customer and switch back.
 *
 *   CUSTOMER – default; can browse, order water, manage their account.
 *   VENDOR   – runs a water business on the platform.
 *   RIDER    – delivers orders.
 *   ADMIN    – platform operator (future).
 */
public enum Role {
    CUSTOMER, VENDOR, RIDER, ADMIN
}
