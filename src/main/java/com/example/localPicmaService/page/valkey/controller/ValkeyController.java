package com.example.localPicmaService.page.valkey.controller;

import cn.hutool.json.JSONObject;
import com.example.localPicmaService.tool.Valkey.ValkeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Valkey 键值管理接口
 */
@RestController
@RequestMapping("/page/valkey/api")
public class ValkeyController {

    @Autowired
    private ValkeyUtil valkeyUtil;

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 设置键值
     */
    @PostMapping("/set")
    public Map<String, Object> set(@RequestBody JSONObject body) {
        String key = body.getStr("key");
        String value = body.getStr("value");
        int ttl = body.getInt("ttl", 0);

        if (key == null || key.isBlank()) return Map.of("success", false, "error", "键名不能为空");

        try {
            if (ttl > 0) {
                valkeyUtil.setEx(key, value, ttl);
            } else {
                valkeyUtil.set(key, value);
            }
            return Map.of("success", true);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 查询键值及属性
     */
    @GetMapping("/get")
    public Map<String, Object> get(@RequestParam String key) {
        try {
            if (!valkeyUtil.exists(key)) {
                return Map.of("success", false, "error", "键不存在");
            }
            String value = valkeyUtil.get(key);
            long ttl = valkeyUtil.ttl(key);
            String type = "string";

            // 尝试获取更详细的类型信息
            try {
                type = redisTemplate.type(key) != null ? redisTemplate.type(key).code() : "string";
            } catch (Exception ignored) {}

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("key", key);
            data.put("value", value);
            data.put("ttl", ttl);
            data.put("type", type);

            return Map.of("success", true, "data", data);
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 删除键
     */
    @PostMapping("/del")
    public Map<String, Object> del(@RequestBody JSONObject body) {
        String key = body.getStr("key");
        if (key == null || key.isBlank()) return Map.of("success", false, "error", "键名不能为空");

        try {
            boolean deleted = valkeyUtil.del(key);
            if (deleted) return Map.of("success", true);
            else return Map.of("success", false, "error", "键不存在");
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}
