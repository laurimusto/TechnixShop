package com.repository;

import com.entity.Spec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpecRepository extends JpaRepository<Spec, Long> {
    List<Spec> findByCategories_CategoryId(Long categoryId);
}
