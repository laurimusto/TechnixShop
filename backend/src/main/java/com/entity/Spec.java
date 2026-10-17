package com.entity;

import com.enums.SpecDataType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "specs")
public class Spec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long specId;

    @Column(nullable = false, unique = true)
    private String key; // e.g. "ram_gb" - stable identifier, used in filter query params

    @Column(nullable = false)
    private String label; // e.g. "RAM (GB)" - shown in the filter panel

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SpecDataType dataType;

    private String unit; // e.g. "GB", "in" - nullable, purely for display

    @Column(nullable = false)
    private boolean filterable = true;

    @ManyToMany
    @JoinTable(
            name = "category_specs",
            joinColumns = @JoinColumn(name = "spec_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new HashSet<>();

}
