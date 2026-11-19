package com.entity;

import com.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(unique = true, nullable = false)
    private String email;

    private String password;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(unique = true)
    private String googleSub;

    // access tokens issued before this moment are rejected; null = password never changed
    private Instant passwordChangedAt;

    // Profile - all optional (see V5 migration). Strings, because none of them are ever used for maths.
    private String firstName;
    private String lastName;
    private String phone;
    private String street;
    private String postalCode;
    private String city;
    private String country;

    // Prebuilt for Project 2: checkout will refuse to place an order until this is true.
    public boolean hasDetailsForOrdering() {
        return isFilled(firstName) && isFilled(lastName) && isFilled(phone)
                && isFilled(street) && isFilled(postalCode) && isFilled(city) && isFilled(country);
    }

    private static boolean isFilled(String value) {
        return value != null && !value.isBlank();
    }
}
