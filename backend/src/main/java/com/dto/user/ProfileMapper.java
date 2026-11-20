package com.dto.user;

import com.entity.User;

// Copies profile data between the DTO and the entity, so the entity never depends on a DTO.
public final class ProfileMapper {

    private ProfileMapper() {
    }

    // A PUT replaces the whole profile: a field sent as empty/null clears it.
    public static void applyTo(ProfileDTO profile, User user) {
        user.setFirstName(clean(profile.firstName()));
        user.setLastName(clean(profile.lastName()));
        user.setPhone(clean(profile.phone()));
        user.setStreet(clean(profile.street()));
        user.setPostalCode(clean(profile.postalCode()));
        user.setCity(clean(profile.city()));
        user.setCountry(clean(profile.country()));
    }

    public static MeResponseDTO toMeResponse(User user) {
        ProfileDTO profile = new ProfileDTO(user.getFirstName(), user.getLastName(), user.getPhone(),
                user.getStreet(), user.getPostalCode(), user.getCity(), user.getCountry());
        return new MeResponseDTO(user.getEmail(), profile, user.getPassword() != null, user.hasDetailsForOrdering(),
                user.isTotpEnabled());
    }

    // trims, and turns "" / "   " into null so "not filled in" has exactly one representation
    private static String clean(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}
