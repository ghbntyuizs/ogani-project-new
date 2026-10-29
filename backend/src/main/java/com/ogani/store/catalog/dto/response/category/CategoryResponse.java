package com.ogani.store.catalog.dto.response.category;

public record CategoryResponse(Integer id, String name, String slug, String imageUrl, Integer parentId) {}
