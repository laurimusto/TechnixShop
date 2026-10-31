package com.service;

import com.dto.category.CategoryResponseDTO;
import com.entity.Category;
import com.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<CategoryResponseDTO> getCategories() {
        List<Category> allCategories = categoryRepository.findAll();

        // group every category by its parent's id, so children of a given category
        // can be looked up without a separate query per node
        Map<Long, List<Category>> childrenByParentId = allCategories.stream()
                .filter(category -> category.getParent() != null)
                .collect(Collectors.groupingBy(category -> category.getParent().getCategoryId()));

        return allCategories.stream()
                .filter(category -> category.getParent() == null)
                .map(root -> toTree(root, childrenByParentId, new HashSet<>()))
                .toList();
    }

    // ancestorIds tracks the current root-to-node path, not every node visited so far -
    // a node reappearing in its own ancestor chain means the data has a cycle, which the
    // single-parent-column schema shouldn't allow but isn't actually prevented by any
    // DB constraint. Fail loudly here instead of recursing forever.
    private CategoryResponseDTO toTree(Category category, Map<Long, List<Category>> childrenByParentId, Set<Long> ancestorIds) {
        Long categoryId = category.getCategoryId();
        if (!ancestorIds.add(categoryId)) {
            throw new IllegalStateException("Cycle detected in category hierarchy at category id " + categoryId);
        }

        List<CategoryResponseDTO> children = childrenByParentId
                .getOrDefault(categoryId, List.of())
                .stream()
                .map(child -> toTree(child, childrenByParentId, ancestorIds))
                .toList();

        ancestorIds.remove(categoryId);

        return new CategoryResponseDTO(categoryId, category.getName(), children);
    }
}
