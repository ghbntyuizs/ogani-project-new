package com.ogani.store.catalog.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Danh sách id ảnh theo thứ tự mới (phải gồm đủ TẤT CẢ ảnh của sản phẩm). */
public record ProductImageOrderRequest(@NotEmpty List<@NotNull Long> imageIds) {}