package com.example.localPicmaService.page.home;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.example.localPicmaService.tool.SQLTool.SqlUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/home/api")
public class HomePageController {

    @GetMapping("/games")
    public List<Map<String, Object>> getGames() throws Exception {
        return fetchModule("games");
    }

    @GetMapping("/photos")
    public Map<String, Object> getPhotos() throws Exception {
        int limit = getConfigMaxDisplay("photos");
        if (!isEnabled("photos")) return Map.of("featured", List.of(), "recent", List.of());

        List<Map<String, Object>> featured = fetchModuleRows("photos_featured", limit);
        List<Map<String, Object>> recent = fetchModuleRows("photos_recent", limit);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("featured", featured);
        result.put("recent", recent);
        return result;
    }

    @GetMapping("/life")
    public List<Map<String, Object>> getLife() throws Exception {
        return fetchModule("life");
    }

    @GetMapping("/work")
    public List<Map<String, Object>> getWork() throws Exception {
        return fetchModule("work");
    }

    // ======================== 内部工具 ========================

    private List<Map<String, Object>> fetchModule(String moduleType) throws Exception {
        int limit = getConfigMaxDisplay(moduleType);
        if (!isEnabled(moduleType)) return List.of();
        return fetchModuleRows(moduleType, limit);
    }

    private List<Map<String, Object>> fetchModuleRows(String moduleType, int limit) throws Exception {
        List<Map<String, Object>> rows = SqlUtil.query(
                "SELECT id, title, content, order_num, date_time, data::text as data, sort_order, create_date "
                        + "FROM home_content WHERE module_type = {?varchar|m?} ORDER BY sort_order, id LIMIT " + limit,
                Map.of("m", moduleType), limit);
        if (rows == null) return List.of();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("title", row.get("title"));
            item.put("content", row.get("content"));
            item.put("order_num", row.get("order_num"));
            item.put("date_time", row.get("date_time"));
            item.put("sort_order", row.get("sort_order"));
            item.put("create_date", row.get("create_date"));

            // 解析图片数组 data::[{file_id, order_num}]
            Object data = row.get("data");
            List<String> imageUrls = new ArrayList<>();
            if (data != null) {
                try {
                    JSONArray arr = JSONUtil.parseArray(data.toString());
                    // 按 order_num 排序
                    arr.sort(Comparator.comparingInt(o -> ((cn.hutool.json.JSONObject) o).getInt("order_num", 0)));
                    for (int i = 0; i < arr.size(); i++) {
                        String fileId = arr.getJSONObject(i).getStr("file_id");
                        if (fileId != null && !fileId.isEmpty()) {
                            imageUrls.add("/api/public/home-image?id=" + fileId);
                        }
                    }
                } catch (Exception ignored) {}
            }
            item.put("images", imageUrls);
            result.add(item);
        }
        return result;
    }

    private int getConfigMaxDisplay(String moduleType) throws Exception {
        Map<String, Object> row = SqlUtil.row(
                "SELECT max_display FROM home_module_config WHERE module_type = {?varchar|m?}",
                Map.of("m", moduleType));
        if (row != null && row.get("max_display") instanceof Number) {
            return ((Number) row.get("max_display")).intValue();
        }
        return 4;
    }

    private boolean isEnabled(String moduleType) throws Exception {
        Map<String, Object> row = SqlUtil.row(
                "SELECT enabled FROM home_module_config WHERE module_type = {?varchar|m?}",
                Map.of("m", moduleType));
        if (row != null) {
            Object enabled = row.get("enabled");
            if (enabled instanceof Boolean) return (Boolean) enabled;
            if (enabled instanceof Number) return ((Number) enabled).intValue() != 0;
        }
        return true;
    }
}
