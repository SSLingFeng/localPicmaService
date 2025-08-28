package com.example.localPicmaService.Controller.Comic;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.example.localPicmaService.base.DataSourceControl;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/ComicControl")
public class ComicControl {

    @PostMapping("/searchComic")
    public ArrayList insertData(@RequestBody(required = false) JSONObject json) {

        Integer page = (Integer) json.get("page");
        Integer page_size = (Integer) json.get("page_size");
        Integer from = (page - 1) * page_size;
        JSONArray manga_source = DataSourceControl.runQuery("select * from manga_source");
        return new ArrayList();
    }

}
