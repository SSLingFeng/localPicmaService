package com.example.localPicmaService.Controller.Comic;


import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.example.localPicmaService.base.DataSourceControl;
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

//        DramVariable.set("username", username);
        String sql = "SELECT * FROM download ";
        JSONArray download = DataSourceControl.runQuery(sql);
//        result.put("download", download);

        String type = (String) json.get("type");
        String path = (String) json.get("path");

        List<JSONObject> insert = new ArrayList<>();
        for (int i = 0; i < download.size(); i++) {


            JSONObject temp = download.getJSONObject(i);
            JSONObject tempInsert = new JSONObject();
            UUID uuid = UUID.randomUUID();
            String uuidStr = uuid.toString();
            tempInsert.put("id", uuidStr);//主键ID


            tempInsert.put("ver", 1);//数据版本
            tempInsert.put("del_flag", 0);//是否删除
            tempInsert.put("create_date", new Date());//数据创建时间
            tempInsert.put("update_date", new Date());//数据更新时间
            tempInsert.put("creator_id", "004SVLG0APRAF");//创建人ID
            tempInsert.put("creator_name", "czw");//创建人名称
            tempInsert.put("updator_id", "004SVLG0APRAF");//更新人ID
            tempInsert.put("updator_name", "czw");//更新人名称

            tempInsert.put("type", type);//分类
            tempInsert.put("picg_id", temp.get("id"));//哔咔ID
            tempInsert.put("title", temp.get("title"));//主标题
            tempInsert.put("subtitle", temp.get("subtitle"));//副标题
            tempInsert.put("download_time", new Date((Long) temp.get("time")));//下载时间
            tempInsert.put("directory", temp.get("directory"));// 存储目录or标题
            tempInsert.put("size", temp.get("size"));//存储大小
            tempInsert.put("json", temp.getJSONObject("json").get("value"));//源数据
            tempInsert.put("path", path);//  相对路径


            JSONObject tempobj = temp.getJSONObject("json");
            JSONObject comicItem = tempobj.getJSONObject("value");
            comicItem = (JSONObject) comicItem.get("comicItem");

            tempInsert.put("creator", comicItem.get("creator"));//  上传者信息
            tempInsert.put("description", comicItem.get("description"));//描述
            tempInsert.put("thumb_url", comicItem.get("thumbUrl"));// 首页图片网址
            tempInsert.put("author", comicItem.get("author"));//  作者
            tempInsert.put("chinese_team", comicItem.get("chineseTeam"));//  汉化团队
            tempInsert.put("categories", comicItem.get("categories").toString());// 漫画分类
            tempInsert.put("tags", comicItem.get("tags").toString());// 标签
            tempInsert.put("likes", comicItem.get("likes"));// 喜欢人数
            tempInsert.put("comments", comicItem.get("comments"));// 评论人数
            Boolean isLiked = (Boolean) comicItem.get("isLiked");
            tempInsert.put("is_liked", isLiked ? 1 : 0);// 可能喜欢
            Boolean isFavourite = (Boolean) comicItem.get("isFavourite");
            tempInsert.put("is_favourite", isFavourite ? 1 : 0);// 可能收藏


            String isoStr = comicItem.get("time").toString();
            Instant instant = Instant.parse(isoStr);        // ISO 8601格式字符串解析
            Date time = Date.from(instant);
            tempInsert.put("time", time);// 发布时间
            tempInsert.put("pages_count", comicItem.get("pagesCount"));// 漫画总页数

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

//删除原表
        DataSourceControl.runQuery("delete from download where id <> '0'");
        return result;
    }
}
