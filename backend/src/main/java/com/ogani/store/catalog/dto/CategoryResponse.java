package com.ogani.store.catalog.dto;

public record CategoryResponse(Integer id, String name, String slug, String imageUrl, Integer parentId) {}
