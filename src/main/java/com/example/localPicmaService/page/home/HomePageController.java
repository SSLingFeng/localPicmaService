package com.example.localPicmaService.page.home;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/home/api")
public class HomePageController {

    @GetMapping("/games")
    public List<Map<String, Object>> getGames() {
        // TODO: 替换为数据库查询
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> game1 = new LinkedHashMap<>();
        game1.put("id", 1);
        game1.put("title", "艾尔登法环");
        game1.put("cover", "https://picsum.photos/seed/eldenring/480/280");
        game1.put("platforms", List.of("PS5", "PC"));
        game1.put("rating", 4.5);
        game1.put("playtime", "128h");
        game1.put("description", "交界地的探索令人沉醉，每一处废墟都藏着惊喜。");
        game1.put("tags", List.of("开放世界", "动作RPG", "魂系"));
        list.add(game1);
        return list;
    }

    @GetMapping("/photos")
    public Map<String, Object> getPhotos() {
        // TODO: 替换为数据库查询
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> featured = new ArrayList<>();
        Map<String, Object> p1 = new LinkedHashMap<>();
        p1.put("id", 1);
        p1.put("url", "https://picsum.photos/seed/mountain1/960/540");
        p1.put("title", "贡嘎银河");
        p1.put("location", "四川·贡嘎");
        featured.add(p1);
        List<Map<String, Object>> recent = new ArrayList<>();
        Map<String, Object> r1 = new LinkedHashMap<>();
        r1.put("id", 4);
        r1.put("url", "https://picsum.photos/seed/street1/400/400");
        r1.put("location", "成都·太古里");
        recent.add(r1);
        result.put("featured", featured);
        result.put("recent", recent);
        return result;
    }

    @GetMapping("/life")
    public List<Map<String, Object>> getLife() {
        // TODO: 替换为数据库查询
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", 1);
        item.put("date", "2025-05-10");
        item.put("title", "周末烘焙实验");
        item.put("category", "日常");
        item.put("tagType", "");
        item.put("color", "#c8956c");
        item.put("content", "第一次尝试做可颂，虽然层次还不够分明，但黄油香气弥漫整个厨房的幸福感是真实的。");
        item.put("images", List.of("https://picsum.photos/seed/bread1/200/200", "https://picsum.photos/seed/bread2/200/200"));
        item.put("important", false);
        list.add(item);
        return list;
    }

    @GetMapping("/work")
    public List<Map<String, Object>> getWork() {
        // TODO: 替换为数据库查询
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> proj = new LinkedHashMap<>();
        proj.put("id", 1);
        proj.put("name", "智能运维平台 v2.0");
        proj.put("status", "进行中");
        proj.put("statusType", "warning");
        proj.put("description", "基于 Spring Boot + Vue 的企业级 AIOps 平台。");
        proj.put("progress", 68);
        proj.put("progressColor", "#c8956c");
        proj.put("techStack", List.of("Spring Boot", "Vue 3", "Elasticsearch", "Flink", "Docker"));
        proj.put("dateRange", "2024.11 — 至今");
        proj.put("link", "#");
        list.add(proj);
        return list;
    }
}
