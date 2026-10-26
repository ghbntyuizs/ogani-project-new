package com.ogani.store.catalog.mapper;

import com.ogani.store.catalog.dto.request.ProductRequest;
import com.ogani.store.catalog.dto.response.ProductResponse;
import com.ogani.store.catalog.entity.Product;
import com.ogani.store.catalog.entity.ProductStatus;
import org.mapstruct.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Mapper(componentModel = "spring", imports = ProductStatus.class, unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface ProductMapper {

    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "onSale", expression = "java(p.getSalePrice() != null)")
    @Mapping(target = "discountPercent", expression = "java(discountPercent(p))")
    @Mapping(target = "inStock", expression = "java(p.getStockQuantity() != null && p.getStockQuantity() > 0 && p.getStatus() == ProductStatus.ACTIVE)")
    ProductResponse toResponse(Product p);

    /** category / các cột hệ thống (version, rating, sold...) KHÔNG map từ request. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "version", ignore = true)
    Product toEntity(ProductRequest r);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "version", ignore = true)
    void update(ProductRequest r, @MappingTarget Product p);

    default Integer discountPercent(Product p) {
        if (p.getSalePrice() == null || p.getPrice() == null || p.getPrice().signum() <= 0) return null;
        return BigDecimal.ONE.subtract(p.getSalePrice().divide(p.getPrice(), 4, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
