package com.example.localPicmaService.api.comic.controller;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.example.localPicmaService.common.DataSourceControl;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/DataControl")
public class DataControl {

    @PostMapping("/insertData")
    public JSONObject insertData(@RequestBody(required = false) JSONObject json) {
        JSONObject result = new JSONObject();
        result.put("result", "success");

        String sql = "SELECT * FROM download ";
        JSONArray download = DataSourceControl.runQuery(sql);

        String type = (String) json.get("type");
        String path = (String) json.get("path");

        List<JSONObject> insert = new ArrayList<>();
        for (int i = 0; i < download.size(); i++) {
            JSONObject temp = download.getJSONObject(i);
            JSONObject tempInsert = new JSONObject();
            UUID uuid = UUID.randomUUID();
            String uuidStr = uuid.toString();
            tempInsert.put("id", uuidStr);
            tempInsert.put("ver", 1);
            tempInsert.put("del_flag", 0);
            tempInsert.put("create_date", new Date());
            tempInsert.put("update_date", new Date());
            tempInsert.put("creator_id", "004SVLG0APRAF");
            tempInsert.put("creator_name", "czw");
            tempInsert.put("updator_id", "004SVLG0APRAF");
            tempInsert.put("updator_name", "czw");
            tempInsert.put("type", type);
            tempInsert.put("picg_id", temp.get("id"));
            tempInsert.put("title", temp.get("title"));
            tempInsert.put("subtitle", temp.get("subtitle"));
            tempInsert.put("download_time", new Date((Long) temp.get("time")));
            tempInsert.put("directory", temp.get("directory"));
            tempInsert.put("size", temp.get("size"));
            tempInsert.put("json", temp.getJSONObject("json").get("value"));
            tempInsert.put("path", path);

            JSONObject tempobj = temp.getJSONObject("json");
            JSONObject comicItem = tempobj.getJSONObject("value");
            comicItem = (JSONObject) comicItem.get("comicItem");
            tempInsert.put("creator", comicItem.get("creator"));
            tempInsert.put("description", comicItem.get("description"));
            tempInsert.put("thumb_url", comicItem.get("thumbUrl"));
            tempInsert.put("author", comicItem.get("author"));
            tempInsert.put("chinese_team", comicItem.get("chineseTeam"));
            tempInsert.put("categories", comicItem.get("categories").toString());
            tempInsert.put("tags", comicItem.get("tags").toString());
            tempInsert.put("likes", comicItem.get("likes"));
            tempInsert.put("comments", comicItem.get("comments"));
            Boolean isLiked = (Boolean) comicItem.get("isLiked");
            tempInsert.put("is_liked", isLiked ? 1 : 0);
            Boolean isFavourite = (Boolean) comicItem.get("isFavourite");
            tempInsert.put("is_favourite", isFavourite ? 1 : 0);

            String isoStr = comicItem.get("time").toString();
            Instant instant = Instant.parse(isoStr);
            Date time = Date.from(instant);
            tempInsert.put("time", time);
            tempInsert.put("pages_count", comicItem.get("pagesCount"));
            tempInsert.put("chapters", comicItem.get("creator"));

            List<String> chapters = tempobj.getJSONObject("value").getJSONArray("chapters").toList(String.class);
            JSONArray chaptersinsert = new JSONArray();
            for (int j = 0; j < chapters.size(); j++) {
                String tempChapter = chapters.get(j);
                JSONObject insetTemp = new JSONObject();
                insetTemp.put("name", tempChapter);
                insetTemp.put("index", j + 1);
                chaptersinsert.put(insetTemp);
            }
            tempInsert.put("chapters", chaptersinsert.toString());
            insert.add(tempInsert);
        }
        result.put("insert", insert);
        DataSourceControl.sync("manga_source", insert, Collections.emptyList(), Collections.emptyList());
        DataSourceControl.runQuery("delete from download where id <> '0'");
        return result;
    }
}
