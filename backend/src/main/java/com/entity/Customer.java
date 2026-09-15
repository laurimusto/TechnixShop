package com.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Customer {

    @Id
    private Long customerId;

    private String customerFirstName;

    private String getCustomerLastName;

    private String street;

    private String city;

    private String zipCode;

    private int houseNumber;

    private int phoneNumber;
}
