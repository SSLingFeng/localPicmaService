package com.example.localPicmaService.tool.Valkey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Valkey / Redis 键值工具类 —— 提供基础的键值存取与删除能力。
 * <p>
 * Valkey 兼容 Redis 协议，底层通过 Spring {@link StringRedisTemplate} 实现。
 * 所有值均为 String 类型。
 * </p>
 *
 * <h3>功能</h3>
 * <ul>
 *   <li>设置键值（支持过期时间）</li>
 *   <li>获取键值</li>
 *   <li>删除键值（单个/批量）</li>
 *   <li>判断键是否存在</li>
 *   <li>设置过期时间</li>
 *   <li>自增/自减</li>
 * </ul>
 */
@Component
public class ValkeyUtil {

    @Autowired
    private StringRedisTemplate redisTemplate;

    // ======================== 写入 ========================

    /**
     * 设置键值（永不过期）
     *
     * @param key   键
     * @param value 值
     */
    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 设置键值（带过期时间）
     *
     * @param key     键
     * @param value   值
     * @param timeout 过期时长
     * @param unit    时间单位
     */
    public void set(String key, String value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 设置键值（过期秒数）
     */
    public void setEx(String key, String value, long seconds) {
        set(key, value, seconds, TimeUnit.SECONDS);
    }

    /**
     * 仅当键不存在时设置（原子操作）
     *
     * @return true=设置成功，false=键已存在
     */
    public boolean setNx(String key, String value, long seconds) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, seconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }

    // ======================== 读取 ========================

    /**
     * 获取键值
     *
     * @param key 键
     * @return 值，键不存在返回 null
     */
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 获取键值并删除（原子操作）
     *
     * @param key 键
     * @return 值，键不存在返回 null
     */
    public String getDel(String key) {
        return redisTemplate.opsForValue().getAndDelete(key);
    }

    /**
     * 批量获取
     *
     * @param keys 键列表
     * @return 值列表（与 keys 顺序一致，不存在的键对应 null）
     */
    public List<String> mGet(String... keys) {
        List<String> result = redisTemplate.opsForValue().multiGet(Arrays.asList(keys));
        return result != null ? result : List.of();
    }

    // ======================== 删除 ========================

    /**
     * 删除单个键
     *
     * @param key 键
     * @return true=删除成功，false=键不存在
     */
    public boolean del(String key) {
        Boolean result = redisTemplate.delete(key);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 批量删除
     *
     * @param keys 键列表
     * @return 实际删除的键数量
     */
    public long del(String... keys) {
        Long count = redisTemplate.delete(Arrays.asList(keys));
        return count != null ? count : 0;
    }

    // ======================== 判断 / 过期 ========================

    /**
     * 判断键是否存在
     */
    public boolean exists(String key) {
        Boolean result = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 设置键的过期时间
     *
     * @param key     键
     * @param timeout 过期时长
     * @param unit    时间单位
     * @return true=设置成功，false=键不存在
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        Boolean result = redisTemplate.expire(key, timeout, unit);
        return Boolean.TRUE.equals(result);
    }

    /**
     * 获取键的剩余过期时间（秒）
     *
     * @return 剩余秒数，-1=永不过期，-2=键不存在
     */
    public long ttl(String key) {
        Long result = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return result != null ? result : -2;
    }

    // ======================== 计数 ========================

    /**
     * 自增（键不存在时初始化为 0 再自增）
     *
     * @param key   键
     * @param delta 增量
     * @return 自增后的值
     */
    public long incrBy(String key, long delta) {
        Long result = redisTemplate.opsForValue().increment(key, delta);
        return result != null ? result : 0;
    }

    /**
     * 自增 1
     */
    public long incr(String key) {
        return incrBy(key, 1);
    }

    /**
     * 自减 1
     */
    public long decr(String key) {
        return incrBy(key, -1);
    }
}
