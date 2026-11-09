package com.service;

import com.dto.product.ProductResponseDTO;
import com.dto.product.ProductSearchCriteriaDTO;
import com.entity.Brand;
import com.entity.Category;
import com.entity.Product;
import com.entity.ProductImage;
import com.repository.ProductImageRepository;
import com.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductImageRepository productImageRepository;

    private ProductService productService;

    private Product product(long id, String name) {
        Category category = new Category();
        category.setCategoryId(1L);
        category.setName("Laptops");

        Brand brand = new Brand();
        brand.setBrandId(1L);
        brand.setName("MSI");

        Product product = new Product();
        product.setProductId(id);
        product.setName(name);
        product.setPrice(new BigDecimal("999.99"));
        product.setStockQuantity(10);
        product.setCategory(category);
        product.setBrand(brand);
        product.setRatingCount(0);
        return product;
    }

    private ProductSearchCriteriaDTO emptyCriteria() {
        return new ProductSearchCriteriaDTO(null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("search: batch-fetches images for the whole page and matches each product by id")
    void search_batchFetchesImagesAndMatchesByProductId() {
        productService = new ProductService(productRepository, productImageRepository);

        Product productA = product(1L, "Laptop A");
        Product productB = product(2L, "Laptop B");
        Pageable pageable = PageRequest.of(0, 20);
        Page<Product> page = new PageImpl<>(List.of(productA, productB), pageable, 2);

        when(productRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        ProductImage imageA = new ProductImage();
        imageA.setProduct(productA);
        imageA.setUrl("https://example.com/a.jpg");

        when(productImageRepository.findByProduct_ProductIdInOrderByDisplayOrderAsc(anyList()))
                .thenReturn(List.of(imageA));

        Page<ProductResponseDTO> result = productService.search(emptyCriteria(), pageable);

        assertThat(result.getContent()).hasSize(2);
        ProductResponseDTO dtoA = result.getContent().stream()
                .filter(dto -> dto.productId().equals(1L)).findFirst().orElseThrow();
        ProductResponseDTO dtoB = result.getContent().stream()
                .filter(dto -> dto.productId().equals(2L)).findFirst().orElseThrow();

        assertThat(dtoA.imageUrl()).isEqualTo("https://example.com/a.jpg");
        assertThat(dtoB.imageUrl()).isNull(); // no image was seeded for product B
    }

    @Test
    @DisplayName("search: never passes a null Specification to the repository, even with an all-null criteria DTO")
    void search_neverPassesNullSpecification() {
        productService = new ProductService(productRepository, productImageRepository);

        Pageable pageable = PageRequest.of(0, 20);
        when(productRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(new PageImpl<>(List.of()));

        productService.search(emptyCriteria(), pageable);

        ArgumentCaptor<Specification<Product>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        verify(productRepository).findAll(specCaptor.capture(), eq(pageable));
        assertThat(specCaptor.getValue()).isNotNull();
    }
}
