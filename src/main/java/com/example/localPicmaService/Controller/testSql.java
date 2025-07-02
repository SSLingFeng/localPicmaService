package com.example.localPicmaService.Controller;


import cn.hutool.json.JSONUtil;
import com.example.localPicmaService.sqlTool.MysqlTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
@Service
public class testSql {
    @Autowired
    private MysqlTool mysqlTool;

    @GetMapping("/testSQL")
    public String testSQL(@RequestParam(value = "name", defaultValue = "World") String name) {
        String sql = "select * from download limit 1";
//        List<Object> list=mysqlTool.executeQueryForList(sql,null);
        List<Object> list = Collections.singletonList(mysqlTool.executeQueryForMapList(sql));
//        System.out.println(list);
        String json = JSONUtil.toJsonStr(list);
        return json;
    }
}
