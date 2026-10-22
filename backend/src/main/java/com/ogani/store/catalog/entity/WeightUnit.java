package com.ogani.store.catalog.entity;

/** DB lưu chữ thường ('kg', 'pcs'...), nên cần converter riêng thay vì @Enumerated. */
public enum WeightUnit {
    G("g"), KG("kg"), ML("ml"), L("l"), PCS("pcs"), PACK("pack");

    private final String dbValue;
    WeightUnit(String dbValue) { this.dbValue = dbValue; }
    public String getDbValue() { return dbValue; }

    public static WeightUnit fromDb(String v) {
        for (WeightUnit u : values()) if (u.dbValue.equals(v)) return u;
        throw new IllegalArgumentException("Unknown weight unit: " + v);
    }
}
