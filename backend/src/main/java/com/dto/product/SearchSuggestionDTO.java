package com.dto.product;

// One row of the search box dropdown. Deliberately tiny (not ProductResponseDTO): it is fetched on
// almost every keystroke, so it carries only what the dropdown shows.
// No productId: several products can share a name (variants), and picking a suggestion searches
// for the name, so one row per name is what the user needs.
public record SearchSuggestionDTO(String name, String brandName) {
}
