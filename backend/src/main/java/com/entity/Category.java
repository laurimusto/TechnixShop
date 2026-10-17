package com.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long categoryId;

    private String name;

    @ManyToOne
    @JoinColumn(name = "parent_id")  //nullable, e.g. "Electronics" wont have an parent.
    private Category parent;

    // which specs (RAM, GPU, CPU, display size, ...) are filterable for this category.
    // join table is owned by Spec; this is the read-navigation side.
    @ManyToMany(mappedBy = "categories")
    private Set<Spec> specs = new HashSet<>();

}
