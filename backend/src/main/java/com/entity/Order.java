package com.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Order {

    @Id
    private Long orderId;

    private String customerName;

    private String toStreet;

    private String toCity;

    private int toZip;

    private int toHouseNumber;

    private int toPhoneNumber;
}
