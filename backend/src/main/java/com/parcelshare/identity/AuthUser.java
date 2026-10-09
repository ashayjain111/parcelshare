package com.parcelshare.identity;

/** The authenticated principal placed in the security context for each request. */
public record AuthUser(Long id, String email, AppUser.Role role) {

    public boolean isAdmin() {
        return role == AppUser.Role.ADMIN;
    }
}
