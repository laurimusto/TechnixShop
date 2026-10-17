package com.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long productId;

    private String productName;

    private int productQuantity;

    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false) //not nullable, e.g. "Electronics" won't have a parent.
    private Category productCategory;

    @ManyToOne
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand productBrand;

    private BigDecimal productPrice;



    private String productImageUrl = "image-url-here";

    private String description;


}
