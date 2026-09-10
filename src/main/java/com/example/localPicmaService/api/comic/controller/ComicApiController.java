package com.example.localPicmaService.api.comic.controller;

import com.example.localPicmaService.tool.SQLTool.SqlUtil;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 漫画纯 API 接口（不被页面调用）
 */
@RestController
@RequestMapping("/api/comic")
public class ComicApiController {

    /**
     * 按 picg_id 查询漫画信息（忽略 del_flag）
     * 返回: id, picg_id, title, author, pages_count
     */
    @GetMapping("/query")
    public Map<String, Object> queryByPicgId(@RequestParam String picgId) throws Exception {
        Map<String, Object> row = SqlUtil.row(
                "SELECT id, picg_id, title, author, pages_count FROM manga_source WHERE picg_id = {?varchar|p?}",
                Map.of("p", picgId));
        if (row == null) return Map.of("success", false, "error", "未找到");
        return Map.of("success", true, "data", row);
    }
}
