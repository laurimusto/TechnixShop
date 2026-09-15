package com.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Product {

    @Id
    private Long productId;

    private String productName;

    private int productQuantity;

    private String productCategory;

    private int productPrice;

    private String productBrand;

    private String productImageUrl = "image-url-here";

}
