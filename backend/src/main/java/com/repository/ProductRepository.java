package com.repository;

import com.dto.product.SearchSuggestionDTO;
import com.entity.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    // Matches the product name or the brand name ("app" finds every Apple product).
    // group by: one row per distinct name, even when several products (variants) share it.
    // Ranking: name starts with the text, then a later word starts with it ("mac" -> "Apple MacBook"),
    // then a match anywhere; alphabetical within each group.
    // "select new ...": reads only two columns - no Product entities loaded.
    // The patterns are bound parameters, so the text can't change the SQL (no injection);
    // the caller escapes % and _ so they match literally instead of acting as LIKE wildcards.
    @Query("""
            select new com.dto.product.SearchSuggestionDTO(p.name, b.name)
            from Product p join p.brand b
            where lower(p.name) like :contains escape '\\'
               or lower(b.name) like :contains escape '\\'
            group by p.name, b.name
            order by case
                       when lower(p.name) like :prefix escape '\\' then 0
                       when lower(p.name) like :wordPrefix escape '\\' then 1
                       else 2
                     end,
                     p.name
            """)
    List<SearchSuggestionDTO> findSuggestions(@Param("contains") String contains,
                                              @Param("prefix") String prefix,
                                              @Param("wordPrefix") String wordPrefix,
                                              Pageable limit);
}
