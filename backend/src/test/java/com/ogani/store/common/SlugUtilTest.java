package com.ogani.store.common;

import com.ogani.store.common.util.SlugUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SlugUtilTest {
    @Test
    void slugifiesLatinAndVietnamese() {
        assertEquals("vegetable-s-package", SlugUtil.slugify("Vegetable's Package"));
        assertEquals("rau-cu-qua-tuoi", SlugUtil.slugify("Rau củ quả tươi"));
        assertEquals("duong-do", SlugUtil.slugify("  Đường  Đỏ -- "));
    }
}
