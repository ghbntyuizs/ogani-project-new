package com.ogani.store.catalog.dto.response;

public record CategoryResponse(Integer id, String name, String slug, String imageUrl, Integer parentId) {}
