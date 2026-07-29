package com.example.localPicmaService.api.comic.controller;

import cn.hutool.json.JSONObject;
import com.example.localPicmaService.tool.SQLTool.SqlUtil;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ComicControl")
public class ComicControl {

    @PostMapping("/searchComic")
    public ArrayList<?> insertData(@RequestBody(required = false) JSONObject json) {
        Integer page = (Integer) json.get("page");
        Integer page_size = (Integer) json.get("page_size");
        Integer from = (page - 1) * page_size;
        try {
            List<Map<String, Object>> manga_source = SqlUtil.query("SELECT * FROM manga_source");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new ArrayList<>();
    }
}
