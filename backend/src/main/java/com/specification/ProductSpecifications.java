package com.specification;

import com.entity.Product;
import com.entity.ProductSpecValue;
import com.entity.Spec;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

public class ProductSpecifications {
    //method for filtering categories.
    public static Specification<Product> hasCategory(Long categoryId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("category").get("categoryId"), categoryId);
    }

    //method for filtering brands.
    public static Specification<Product> hasBrand(Long brandId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(root.get("brand").get("brandId"), brandId);
    }

    //this is for filtering prices and weight between x AND y
    public static Specification<Product> numericBetween(String fieldName, BigDecimal min, BigDecimal max) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.between(root.get(fieldName), min, max);
    }

    //CPU and GPU filtering
    public static Specification<Product> hasSpecTextIn(String specKey, List<String> values) {
        return (root, query, criteriaBuilder) -> {
            Subquery<ProductSpecValue> subquery = query.subquery(ProductSpecValue.class);
            Root<ProductSpecValue> specValueRoot = subquery.from(ProductSpecValue.class);

            Predicate correlatesToOuterProduct = criteriaBuilder.equal(specValueRoot.get("product"), root);
            Predicate matchesSpecKey = criteriaBuilder.equal(specValueRoot.get("spec").get("key"), specKey);
            Predicate matchesValue = specValueRoot.get("valueText").in(values);

            Predicate finalPredicate = criteriaBuilder.and(correlatesToOuterProduct, matchesSpecKey, matchesValue);
            subquery.select(specValueRoot).where(finalPredicate);
            return criteriaBuilder.exists(subquery);
        };


    }

    //RAM and display size filtering
    public static Specification<Product> hasSpecNumberIn(String specKey, List<BigDecimal> values) {
        return (root, query, criteriaBuilder) -> {
            Subquery<ProductSpecValue> subquery = query.subquery(ProductSpecValue.class);
            Root<ProductSpecValue> specValueRoot = subquery.from(ProductSpecValue.class);

            Predicate correlatesToOuterProduct = criteriaBuilder.equal(specValueRoot.get("product"), root);
            Predicate matchesSpecKey = criteriaBuilder.equal(specValueRoot.get("spec").get("key"), specKey);
            Predicate matchesValue = specValueRoot.get("valueNumber").in(values);

            Predicate finalPredicate = criteriaBuilder.and(correlatesToOuterProduct, matchesSpecKey, matchesValue);
            subquery.select(specValueRoot).where(finalPredicate);
            return criteriaBuilder.exists(subquery);
        };

    }
}
