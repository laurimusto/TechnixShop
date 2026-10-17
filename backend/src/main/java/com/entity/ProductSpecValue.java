package com.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "product_spec_values", uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "spec_id"}))
public class ProductSpecValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spec_id", nullable = false)
    private Spec spec;

    // exactly one of these two is populated, depending on spec.dataType
    private BigDecimal valueNumber;

    private String valueText;

}
