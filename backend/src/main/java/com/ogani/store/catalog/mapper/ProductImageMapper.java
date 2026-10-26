package com.ogani.store.catalog.mapper;

import com.ogani.store.catalog.dto.response.ProductImageResponse;
import com.ogani.store.catalog.entity.ProductImage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProductImageMapper {

    @Mapping(target = "productId", source = "product.id")
    ProductImageResponse toResponse(ProductImage image);
}