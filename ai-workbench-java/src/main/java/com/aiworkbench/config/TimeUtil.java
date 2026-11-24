package com.aiworkbench.config;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** 时间格式工具：统一输出 yyyy-MM-dd HH:mm:ss */
public class TimeUtil {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String now() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(FMT);
    }

    public static String format(String isoOrAnything) {
        if (isoOrAnything == null || isoOrAnything.isEmpty()) return "";
        // 已经是 yyyy-MM-dd HH:mm:ss 格式 → 原样返回
        if (isoOrAnything.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) return isoOrAnything;
        // ISO 格式 2026-11-23T05:20:55.305327900Z → 转
        try {
            return LocalDateTime.parse(isoOrAnything.replace("Z", "").replaceAll("\\.\\d+$", "").replace("T", " "))
                .format(FMT);
        } catch (Exception e) {
            // 兜底：只保留前 19 位 yyyy-MM-ddTHH:mm:ss
            String s = isoOrAnything.replace("T", " ");
            return s.length() > 19 ? s.substring(0, 19) : s;
        }
    }
}
