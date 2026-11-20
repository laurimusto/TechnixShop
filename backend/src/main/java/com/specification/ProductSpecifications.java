package com.specification;

import com.entity.Product;
import com.entity.ProductSpecValue;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

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

    // In LIKE, % (any text) and _ (any one character) are wildcards. Escaping them, and the escape
    // character itself, makes the user's text match literally: "50%" means "50%", not "50 + anything".
    // The backslash goes first, otherwise it would also double the backslashes added for % and _.
    public static final char LIKE_ESCAPE = '\\';

    public static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    //case-insensitive substring match on product name, for the search bar
    public static Specification<Product> nameContains(String name) {
        String pattern = "%" + escapeLike(name.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern, LIKE_ESCAPE);
    }

    //this is for filtering prices and weight between x AND y
    public static Specification<Product> numericBetween(String fieldName, BigDecimal min, BigDecimal max) {
        return (root, query, criteriaBuilder) -> {
            if(min != null && max != null) {
                return criteriaBuilder.between(root.get(fieldName), min, max);
            } else if ( min != null) {
                return criteriaBuilder.greaterThanOrEqualTo(root.get(fieldName), min);
            } else if (max != null) {
                return criteriaBuilder.lessThanOrEqualTo(root.get(fieldName), max);
            } else {
                return criteriaBuilder.conjunction();
            }
        };
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
