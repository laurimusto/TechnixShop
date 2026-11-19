package com.dto.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// The only fields a user may change about themselves via PUT /me (and optionally send at /register).
// Deliberately no email, role or password here: Jackson can only fill fields that exist, so a client
// sending {"role":"ADMIN"} gets nothing - this is the defence against "mass assignment".
// Every field is optional; empty strings are stored as null (see ProfileMapper).
public record ProfileDTO(
        @Size(max = 50, message = "First name can be at most 50 characters") String firstName,
        @Size(max = 50, message = "Last name can be at most 50 characters") String lastName,
        // optional leading +, then digits, spaces, dashes or brackets; empty is allowed
        @Pattern(regexp = "^$|^\\+?[0-9 ()-]{6,20}$", message = "Phone number may contain only digits, spaces, dashes, brackets and a leading +")
        String phone,
        @Size(max = 100, message = "Street can be at most 100 characters") String street,
        @Size(max = 20, message = "Postal code can be at most 20 characters") String postalCode,
        @Size(max = 50, message = "City can be at most 50 characters") String city,
        @Size(max = 56, message = "Country can be at most 56 characters") String country) {
}
