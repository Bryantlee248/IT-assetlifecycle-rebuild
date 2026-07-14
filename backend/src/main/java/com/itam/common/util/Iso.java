package com.itam.common.util;

import java.time.Instant;

/**
 * Instant → ISO-8601 字符串工具，统一 DTO 时间字段格式（前端按字符串解析）。
 */
public final class Iso {

    private Iso() {
    }

    public static String of(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
