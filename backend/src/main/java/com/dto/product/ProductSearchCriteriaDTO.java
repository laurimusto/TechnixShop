package com.dto.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProductSearchCriteriaDTO(Long categoryId,
                                       Long brandId,
                                       BigDecimal minPrice,
                                       BigDecimal maxPrice,
                                       Map<String, List<String>> textSpecFilters, //spec key → acceptable text values
                                       Map<String, List<BigDecimal>> numberSpecFilters // spec key → acceptable numeric values

) {
}
