package com.example.localPicmaService.api.comic.controller;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.example.localPicmaService.tool.SQLTool.SqlUtil;
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
    public JSONObject insertData(@RequestBody(required = false) JSONObject json) throws Exception {
        JSONObject result = new JSONObject();
        result.put("result", "success");

        List<Map<String, Object>> download = SqlUtil.query("SELECT * FROM download");

        String type = (String) json.get("type");
        String path = (String) json.get("path");

        List<Map<String, Object>> insert = new ArrayList<>();
        for (int i = 0; i < download.size(); i++) {
            Map<String, Object> temp = download.get(i);
            Map<String, Object> tempInsert = new LinkedHashMap<>();
            UUID uuid = UUID.randomUUID();
            String uuidStr = uuid.toString();
            tempInsert.put("id", uuidStr); //
            tempInsert.put("ver", 1);//
            tempInsert.put("del_flag", 0);//
            tempInsert.put("create_date", new Date());//创建时间
            tempInsert.put("update_date", new Date());//更新时间
            tempInsert.put("creator_id", "004SVLG0APRAF");//创建人ID
            tempInsert.put("creator_name", "czw");//创建人
            tempInsert.put("updator_id", "004SVLG0APRAF");//更新人ID
            tempInsert.put("updator_name", "czw");//最近更新人
            tempInsert.put("type", type);// 类型 coser 或者漫画  对应 单极文件路径的 coser  或 cartoon
            tempInsert.put("picg_id", temp.get("id"));// 下载的 漫画源的漫画ID，
            tempInsert.put("title", temp.get("title"));//标题
            tempInsert.put("subtitle", temp.get("subtitle"));//副标题
            tempInsert.put("download_time", new Date((Long) temp.get("time")));//下载时间
            tempInsert.put("directory", temp.get("directory"));//存储的目录
            tempInsert.put("size", temp.get("size"));//存储大小
            tempInsert.put("json", ((JSONObject) temp.get("json")).get("value"));//数据源，对sqllit文件里的表字段json
            tempInsert.put("path", path);//路径，对应传入参数的路径 例如： 20250610 单级文件夹路径

            JSONObject tempobj = (JSONObject) temp.get("json");
            JSONObject comicItem = tempobj.getJSONObject("value");//
            comicItem = (JSONObject) comicItem.get("comicItem");//
            tempInsert.put("creator", comicItem.get("creator"));//上传者信息
            tempInsert.put("description", comicItem.get("description"));//漫画的表述
            tempInsert.put("thumb_url", comicItem.get("thumbUrl"));//宣传页图片路径
            tempInsert.put("author", comicItem.get("author"));//作者
            tempInsert.put("chinese_team", comicItem.get("chineseTeam"));//汉化团队
            tempInsert.put("categories", comicItem.get("categories").toString());//漫画类别， 这是个String数组
            tempInsert.put("tags", comicItem.get("tags").toString());//漫画标签， 也是个String数组
            tempInsert.put("likes", comicItem.get("likes"));//喜欢的人数
            tempInsert.put("comments", comicItem.get("comments"));//评论人数
            Boolean isLiked = (Boolean) comicItem.get("isLiked");//
            tempInsert.put("is_liked", isLiked ? 1 : 0);// 可能喜欢
            Boolean isFavourite = (Boolean) comicItem.get("isFavourite");//
            tempInsert.put("is_favourite", isFavourite ? 1 : 0);//可能收藏

            String isoStr = comicItem.get("time").toString();//
            Instant instant = Instant.parse(isoStr);//
            Date time = Date.from(instant);//
            tempInsert.put("time", time);//发布时间
            tempInsert.put("pages_count", comicItem.get("pagesCount"));//漫画总页数
            tempInsert.put("chapters", comicItem.get("creator"));//每个章节对用的名称， 这是个json数组 [{"name": "第1話", "index": 1}]

            List<String> chapters = tempobj.getJSONObject("value").getJSONArray("chapters").toList(String.class);//
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

        SqlUtil.sync("manga_source").insert(insert).commit();
        SqlUtil.exec("DELETE FROM download WHERE id <> '0'");
        return result;
    }
}
