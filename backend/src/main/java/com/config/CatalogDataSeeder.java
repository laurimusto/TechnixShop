package com.config;

import com.entity.Brand;
import com.entity.Category;
import com.entity.Product;
import com.entity.ProductImage;
import com.entity.ProductSpecValue;
import com.entity.Spec;
import com.repository.BrandRepository;
import com.repository.CategoryRepository;
import com.repository.ProductImageRepository;
import com.repository.ProductRepository;
import com.repository.ProductSpecValueRepository;
import com.repository.SpecRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

// Dev-only demo data: 30+ laptops and 30+ phones with random prices/specs, so the catalog
// and faceted search have something real to query against. Skips entirely if products
// already exist, so restarts don't keep piling on duplicate rows.
@Component
public class CatalogDataSeeder implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final SpecRepository specRepository;
    private final ProductSpecValueRepository productSpecValueRepository;
    private final ProductImageRepository productImageRepository;

    private final Random random = new Random(42);

    public CatalogDataSeeder(CategoryRepository categoryRepository,
                              BrandRepository brandRepository,
                              ProductRepository productRepository,
                              SpecRepository specRepository,
                              ProductSpecValueRepository productSpecValueRepository,
                              ProductImageRepository productImageRepository) {
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.productRepository = productRepository;
        this.specRepository = specRepository;
        this.productSpecValueRepository = productSpecValueRepository;
        this.productImageRepository = productImageRepository;
    }

    private static final Map<String, List<String>> LAPTOP_LINES_BY_BRAND = Map.of(
            "Lenovo", List.of("ThinkPad X1 Carbon", "Legion 5", "IdeaPad Slim 5"),
            "Dell", List.of("XPS 13", "Inspiron 15", "Alienware m16"),
            "ASUS", List.of("ROG Strix G16", "ZenBook 14", "VivoBook Pro 15"),
            "Apple", List.of("MacBook Air 13", "MacBook Pro 14", "MacBook Pro 16"),
            "HP", List.of("Pavilion 15", "Spectre x360", "Omen 16"),
            "Acer", List.of("Aspire 5", "Predator Helios 16", "Swift Go 14"),
            "MSI", List.of("Katana 15", "Stealth 16", "Modern 14")
    );

    private static final Map<String, List<String>> PHONE_LINES_BY_BRAND = Map.of(
            "Apple", List.of("iPhone 15", "iPhone 15 Pro", "iPhone 15 Pro Max"),
            "Samsung", List.of("Galaxy S24", "Galaxy S24 Ultra", "Galaxy A55"),
            "Google", List.of("Pixel 8", "Pixel 8 Pro", "Pixel 8a"),
            "OnePlus", List.of("OnePlus 12", "OnePlus 12R", "OnePlus Open"),
            "Xiaomi", List.of("Redmi Note 13", "Xiaomi 14", "Poco F6")
    );

    private static final List<String> LAPTOP_CPUS = List.of(
            "Intel Core i5", "Intel Core i7", "Intel Core i9",
            "AMD Ryzen 5", "AMD Ryzen 7", "AMD Ryzen 9", "Apple M3"
    );
    private static final List<String> LAPTOP_GPUS = List.of(
            "Integrated Graphics", "NVIDIA RTX 3050", "NVIDIA RTX 4060",
            "NVIDIA RTX 4070", "AMD Radeon RX 6600M"
    );
    private static final List<BigDecimal> LAPTOP_RAM_GB = List.of(
            BigDecimal.valueOf(8), BigDecimal.valueOf(16), BigDecimal.valueOf(32), BigDecimal.valueOf(64)
    );
    private static final List<BigDecimal> LAPTOP_DISPLAY_IN = List.of(
            new BigDecimal("13.3"), new BigDecimal("14.0"), new BigDecimal("15.6"),
            new BigDecimal("16.0"), new BigDecimal("17.3")
    );

    private static final List<String> PHONE_CPUS = List.of(
            "Snapdragon 8 Gen 3", "Apple A17 Pro", "Exynos 2400", "Dimensity 9300", "Tensor G3"
    );
    private static final List<BigDecimal> PHONE_RAM_GB = List.of(
            BigDecimal.valueOf(4), BigDecimal.valueOf(6), BigDecimal.valueOf(8),
            BigDecimal.valueOf(12), BigDecimal.valueOf(16)
    );
    private static final List<BigDecimal> PHONE_DISPLAY_IN = List.of(
            new BigDecimal("6.1"), new BigDecimal("6.4"), new BigDecimal("6.5"),
            new BigDecimal("6.7"), new BigDecimal("6.8")
    );

    @Override
    public void run(String... args) {
        if (productRepository.count() > 0) {
            return;
        }

        Category laptops = createCategory("Laptops");
        Category phones = createCategory("Phones");

        Map<String, Spec> specsByKey = specRepository.findAll().stream()
                .collect(Collectors.toMap(Spec::getKey, spec -> spec));

        linkSpecToCategory(specsByKey.get("cpu"), laptops);
        linkSpecToCategory(specsByKey.get("gpu"), laptops);
        linkSpecToCategory(specsByKey.get("ram_gb"), laptops);
        linkSpecToCategory(specsByKey.get("display_size_in"), laptops);

        linkSpecToCategory(specsByKey.get("cpu"), phones);
        linkSpecToCategory(specsByKey.get("ram_gb"), phones);
        linkSpecToCategory(specsByKey.get("display_size_in"), phones);

        Map<String, Brand> brands = brandRepository.findAll().stream()
                .collect(Collectors.toMap(Brand::getName, brand -> brand));

        seedLaptops(laptops, specsByKey, brands, 32);
        seedPhones(phones, specsByKey, brands, 32);
    }

    private void seedLaptops(Category category, Map<String, Spec> specsByKey, Map<String, Brand> brands, int count) {
        List<String> brandNames = List.copyOf(LAPTOP_LINES_BY_BRAND.keySet());

        for (int i = 0; i < count; i++) {
            String brandName = pick(brandNames);
            Brand brand = getOrCreateBrand(brandName, brands);
            String line = pick(LAPTOP_LINES_BY_BRAND.get(brandName));

            Product product = new Product();
            product.setName(brandName + " " + line);
            product.setDescription(line + " from " + brandName + " - everyday performance and portability.");
            product.setPrice(randomPrice(450, 2800));
            product.setStockQuantity(random.nextInt(201));
            product.setCategory(category);
            product.setBrand(brand);
            product.setWeightKg(randomDecimal(1.1, 2.6, 3));
            product.setLengthCm(randomDecimal(30, 36, 2));
            product.setWidthCm(randomDecimal(20, 25, 2));
            product.setHeightCm(randomDecimal(1.5, 2.5, 2));
            product.setAverageRating(randomDecimal(3.0, 5.0, 1));
            product.setRatingCount(random.nextInt(800));
            productRepository.save(product);

            saveTextSpec(product, specsByKey.get("cpu"), pick(LAPTOP_CPUS));
            saveTextSpec(product, specsByKey.get("gpu"), pick(LAPTOP_GPUS));
            saveNumberSpec(product, specsByKey.get("ram_gb"), pick(LAPTOP_RAM_GB));
            saveNumberSpec(product, specsByKey.get("display_size_in"), pick(LAPTOP_DISPLAY_IN));

            saveDefaultImage(product);
        }
    }

    private void seedPhones(Category category, Map<String, Spec> specsByKey, Map<String, Brand> brands, int count) {
        List<String> brandNames = List.copyOf(PHONE_LINES_BY_BRAND.keySet());

        for (int i = 0; i < count; i++) {
            String brandName = pick(brandNames);
            Brand brand = getOrCreateBrand(brandName, brands);
            String line = pick(PHONE_LINES_BY_BRAND.get(brandName));

            Product product = new Product();
            product.setName(brandName + " " + line);
            product.setDescription(line + " from " + brandName + " - the latest in mobile technology.");
            product.setPrice(randomPrice(250, 1600));
            product.setStockQuantity(random.nextInt(201));
            product.setCategory(category);
            product.setBrand(brand);
            product.setWeightKg(randomDecimal(0.14, 0.24, 3));
            product.setLengthCm(randomDecimal(14, 17, 2));
            product.setWidthCm(randomDecimal(7, 8, 2));
            product.setHeightCm(randomDecimal(0.7, 0.9, 2));
            product.setAverageRating(randomDecimal(3.0, 5.0, 1));
            product.setRatingCount(random.nextInt(800));
            productRepository.save(product);

            saveTextSpec(product, specsByKey.get("cpu"), pick(PHONE_CPUS));
            saveNumberSpec(product, specsByKey.get("ram_gb"), pick(PHONE_RAM_GB));
            saveNumberSpec(product, specsByKey.get("display_size_in"), pick(PHONE_DISPLAY_IN));

            saveDefaultImage(product);
        }
    }

    private Category createCategory(String name) {
        Category category = new Category();
        category.setName(name);
        return categoryRepository.save(category);
    }

    private void linkSpecToCategory(Spec spec, Category category) {
        if (spec == null) {
            return;
        }
        spec.getCategories().add(category);
        specRepository.save(spec);
    }

    private Brand getOrCreateBrand(String name, Map<String, Brand> brands) {
        return brands.computeIfAbsent(name, brandName -> {
            Brand brand = new Brand();
            brand.setName(brandName);
            return brandRepository.save(brand);
        });
    }

    private void saveTextSpec(Product product, Spec spec, String value) {
        if (spec == null) {
            return;
        }
        ProductSpecValue specValue = new ProductSpecValue();
        specValue.setProduct(product);
        specValue.setSpec(spec);
        specValue.setValueText(value);
        productSpecValueRepository.save(specValue);
    }

    private void saveNumberSpec(Product product, Spec spec, BigDecimal value) {
        if (spec == null) {
            return;
        }
        ProductSpecValue specValue = new ProductSpecValue();
        specValue.setProduct(product);
        specValue.setSpec(spec);
        specValue.setValueNumber(value);
        productSpecValueRepository.save(specValue);
    }

    private void saveDefaultImage(Product product) {
        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setUrl("https://picsum.photos/seed/" + product.getProductId() + "/600/400");
        image.setDisplayOrder(0);
        productImageRepository.save(image);
    }

    private <T> T pick(List<T> options) {
        return options.get(random.nextInt(options.size()));
    }

    private BigDecimal randomPrice(double min, double max) {
        return randomDecimal(min, max, 2);
    }

    private BigDecimal randomDecimal(double min, double max, int scale) {
        double value = min + random.nextDouble() * (max - min);
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}
