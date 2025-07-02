package com.example.localPicmaService.base;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DramVariable {
    private static final ThreadLocal<Map<String, Object>> userData = ThreadLocal.withInitial(HashMap::new);

    public static void set(String key, Object value) {
        userData.get().put(key, value);
    }

    public static Object get(String key) {
        return userData.get().get(key);
    }

    public static void clear() {
        userData.remove(); // 一定记得清理，避免内存泄漏
    }

    public static Map<String, Object> getAll() {
        return userData.get();
    }
}
