package com.example.localPicmaService.base;

import java.util.Map;

public class DramVariable {
    private Map<String, Object> info;
    public void set(String key, Object value) {
        this.info.put(key, value);
    }

    public Object get(String key) {
        return this.info.get(key);
    }
}
