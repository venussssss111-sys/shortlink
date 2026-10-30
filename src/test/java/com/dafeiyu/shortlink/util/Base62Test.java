package com.dafeiyu.shortlink.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Base62Test {

    @Test
    void encode_should_convert_decimal_to_base62() {
        assertEquals("0", Base62.encode(0));
        assertEquals("1", Base62.encode(1));
        // 62 在 62 进制里就是 "10"
        assertEquals("10", Base62.encode(62));
        // 62 * 62 = 3844
        assertEquals("100", Base62.encode(3844));
    }

    @Test
    void encode_should_reject_negative() {
        assertThrows(IllegalArgumentException.class, () -> Base62.encode(-1));
    }

    @Test
    void random_should_return_requested_length() {
        String code = Base62.random(6);
        assertEquals(6, code.length());
        assertTrue(code.matches("^[a-zA-Z0-9]{6}$"));
    }

    @Test
    void random_should_be_unique_in_practice() {
        // 568 亿种组合，1000 次基本不会碰撞
        assertNotEquals(Base62.random(6), Base62.random(6));
    }
}
