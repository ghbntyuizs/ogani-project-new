package com.ogani.store.catalog.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class WeightUnitConverter implements AttributeConverter<WeightUnit, String> {
    @Override public String convertToDatabaseColumn(WeightUnit unit) { return unit == null ? null : unit.getDbValue(); }
    @Override public WeightUnit convertToEntityAttribute(String db) { return db == null ? null : WeightUnit.fromDb(db); }
}
