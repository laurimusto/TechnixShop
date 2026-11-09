package com.service;

import com.dto.category.CategoryResponseDTO;
import com.entity.Category;
import com.repository.CategoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    private CategoryService categoryService;

    private Category category(long id, String name, Category parent) {
        Category category = new Category();
        category.setCategoryId(id);
        category.setName(name);
        category.setParent(parent);
        return category;
    }

    @Test
    @DisplayName("getCategories: flat root categories with no children map to DTOs with empty children lists")
    void getCategories_flatRoots() {
        categoryService = new CategoryService(categoryRepository);
        Category laptops = category(1L, "Laptops", null);
        Category phones = category(2L, "Phones", null);
        when(categoryRepository.findAll()).thenReturn(List.of(laptops, phones));

        List<CategoryResponseDTO> result = categoryService.getCategories();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CategoryResponseDTO::name).containsExactlyInAnyOrder("Laptops", "Phones");
        assertThat(result).allSatisfy(dto -> assertThat(dto.children()).isEmpty());
    }

    @Test
    @DisplayName("getCategories: nested categories are grouped under their parent, not flattened")
    void getCategories_nestedHierarchy() {
        categoryService = new CategoryService(categoryRepository);
        Category laptops = category(1L, "Laptops", null);
        Category gamingLaptops = category(2L, "Gaming Laptops", laptops);
        when(categoryRepository.findAll()).thenReturn(List.of(laptops, gamingLaptops));

        List<CategoryResponseDTO> result = categoryService.getCategories();

        assertThat(result).hasSize(1);
        CategoryResponseDTO root = result.get(0);
        assertThat(root.name()).isEqualTo("Laptops");
        assertThat(root.children()).hasSize(1);
        assertThat(root.children().get(0).name()).isEqualTo("Gaming Laptops");
    }

    @Test
    @DisplayName("toTree: a cyclic parent chain throws IllegalStateException instead of recursing forever")
    void toTree_cyclicDataThrowsInsteadOfRecursingForever() {
        categoryService = new CategoryService(categoryRepository);

        // A single-parent-column schema can't produce a cycle reachable from a real root (see
        // CategoryService's own comment), so this exercises the guard directly via reflection,
        // bypassing the "start only from roots" entry point - simulating what would happen if a
        // future refactor ever fed toTree a cyclic subgraph.
        Category categoryA = category(1L, "A", null);
        Category categoryB = category(2L, "B", null);
        categoryA.setParent(categoryB);
        categoryB.setParent(categoryA);

        Map<Long, List<Category>> childrenByParentId = new HashMap<>();
        childrenByParentId.put(1L, List.of(categoryB)); // B is "child of" A
        childrenByParentId.put(2L, List.of(categoryA)); // A is "child of" B

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                categoryService, "toTree", categoryA, childrenByParentId, new HashSet<Long>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cycle detected");
    }
}
