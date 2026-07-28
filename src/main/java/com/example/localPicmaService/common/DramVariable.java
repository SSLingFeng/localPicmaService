package com.example.localPicmaService.common;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DramVariable {
    private static final Map<String, ThreadLocal<Map<String, Object>>> userData =
            new ConcurrentHashMap<>(new HashMap<>());

    public static Map<String, Object> getAll() {
        return userData.getOrDefault(Thread.currentThread().getName(), ThreadLocal.withInitial(HashMap::new)).get();
    }

    public static void set(String key, Object value) {
        userData.computeIfAbsent(Thread.currentThread().getName(), k -> ThreadLocal.withInitial(HashMap::new))
               .get()
               .put(key, value);
    }

    public static Object get(String key) {
        return userData.getOrDefault(Thread.currentThread().getName(), ThreadLocal.withInitial(HashMap::new))
                      .get()
                      .get(key);
    }

    public static void clear() {
        userData.forEach((threadName, threadLocalMap) -> {
            if (Thread.currentThread().getName().equals(threadName)) {
                threadLocalMap.remove();
            }
        });
    }
}
