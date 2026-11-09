package com.dto.product;

import com.entity.Brand;
import com.entity.Category;
import com.entity.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductResponseMapperTest {

    private Product baseProduct() {
        Category category = new Category();
        category.setCategoryId(1L);
        category.setName("Laptops");

        Brand brand = new Brand();
        brand.setBrandId(2L);
        brand.setName("MSI");

        Product product = new Product();
        product.setProductId(10L);
        product.setName("MSI Stealth 16");
        product.setDescription("Stealth 16 from MSI");
        product.setPrice(new BigDecimal("1475.75"));
        product.setStockQuantity(47);
        product.setCategory(category);
        product.setBrand(brand);
        product.setAverageRating(new BigDecimal("3.5"));
        product.setRatingCount(127);
        return product;
    }

    @Test
    @DisplayName("from: flattens category/brand to plain names and converts metric dimensions to imperial")
    void from_mapsFieldsAndConvertsUnits() {
        Product product = baseProduct();
        product.setWeightKg(new BigDecimal("2.496"));
        product.setLengthCm(new BigDecimal("34.79"));
        product.setWidthCm(new BigDecimal("20.75"));
        product.setHeightCm(new BigDecimal("1.84"));

        ProductResponseDTO dto = ProductResponseMapper.from(product, "https://example.com/image.jpg");

        assertThat(dto.productId()).isEqualTo(10L);
        assertThat(dto.categoryName()).isEqualTo("Laptops");
        assertThat(dto.brandName()).isEqualTo("MSI");
        assertThat(dto.imageUrl()).isEqualTo("https://example.com/image.jpg");
        assertThat(dto.weightKg()).isEqualByComparingTo("2.496");
        assertThat(dto.weightLb()).isEqualByComparingTo("5.50");
        assertThat(dto.lengthCm()).isEqualByComparingTo("34.79");
        assertThat(dto.lengthIn()).isEqualByComparingTo("13.70");
    }

    @Test
    @DisplayName("from: missing metric dimensions map to null imperial fields, not an exception")
    void from_nullDimensionsMapToNullImperial() {
        Product product = baseProduct(); // weight/length/width/height left null

        ProductResponseDTO dto = ProductResponseMapper.from(product, null);

        assertThat(dto.weightKg()).isNull();
        assertThat(dto.weightLb()).isNull();
        assertThat(dto.lengthIn()).isNull();
        assertThat(dto.widthIn()).isNull();
        assertThat(dto.heightIn()).isNull();
        assertThat(dto.imageUrl()).isNull();
    }
}
