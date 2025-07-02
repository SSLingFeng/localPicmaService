package com.example.localPicmaService.base;

import io.lettuce.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class DataSourceControl {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\?([^{}?\\n]+)\\?\\}");

    //新增
    public List<JsonObject> insert(String tableName, List<JsonObject> data) {
        List<JsonObject> result = new ArrayList<>();


        return result;
    }

    //更新
    public List<JsonObject> update(String tableName, List<JsonObject> data) {
        List<JsonObject> result = new ArrayList<>();


        return result;
    }

    //删除
    public List<JsonObject> delete(String tableName, List<JsonObject> data) {
        List<JsonObject> result = new ArrayList<>();


        return result;
    }

    //更新
    public void sync(String tableName, List<JsonObject> insert, List<JsonObject> update, List<JsonObject> delete) {
        if (!insert.isEmpty()) this.insert(tableName, insert);
        if (!update.isEmpty()) this.update(tableName, insert);
        if (!delete.isEmpty()) this.delete(tableName, delete);
    }


    //执行SQL
    public JsonObject runQuery(String sql) {
        JsonObject result = null;
        return result;
    }












    public static String dispose(String sql){




        Matcher matcher = PLACEHOLDER_PATTERN.matcher(sql);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String key = matcher.group(1);
            Object rawValue = DramVariable.get(key);

            String safeValue = convertToSqlLiteral(rawValue);

            // 替换成 SQL 安全值
            matcher.appendReplacement(result, Matcher.quoteReplacement(safeValue));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    // 转换为 SQL 安全字面量
    private static String convertToSqlLiteral(Object value) {
        if (value == null) {
            return "NULL";
        }

        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }

        // 其他类型统一作为字符串处理，注意 SQL 注入风险
        String str = value.toString();
        String escaped = str.replace("'", "''"); // PostgreSQL 的转义方式
        return "'" + escaped + "'";
    }







}
