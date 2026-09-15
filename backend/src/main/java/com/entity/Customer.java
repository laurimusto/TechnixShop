package com.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

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
