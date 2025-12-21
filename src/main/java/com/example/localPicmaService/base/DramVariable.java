package com.example.localPicmaService.base;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DramVariable {
    // 使用ConcurrentHashMap来保证多线程访问时的线程安全
    private static final Map<String, ThreadLocal<Map<String, Object>>> userData =
            new ConcurrentHashMap<>(new HashMap<>());

    // 获取当前用户下的所有线程的变量
    public static Map<String, Object> getAll() {
        return userData.getOrDefault(Thread.currentThread().getName(), ThreadLocal.withInitial(HashMap::new)).get();
    }

    // 设置当前用户下当前线程的变量
    public static void set(String key, Object value) {
        userData.computeIfAbsent(Thread.currentThread().getName(), k -> ThreadLocal.withInitial(HashMap::new))
               .get()
               .put(key, value);
    }

    // 获取当前用户下当前线程的变量
    public static Object get(String key) {
        return userData.getOrDefault(Thread.currentThread().getName(), ThreadLocal.withInitial(HashMap::new))
                      .get()
                      .get(key);
    }

    // 清理当前用户的所有线程数据
    public static void clear() {
        userData.forEach((threadName, threadLocalMap) -> {
            if (Thread.currentThread().getName().equals(threadName)) {
                threadLocalMap.remove(); // 清理特定线程的数据
            }
        });
    }
}