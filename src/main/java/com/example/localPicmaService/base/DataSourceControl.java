package com.example.localPicmaService.base;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DataSourceControl {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\?([^{}?\\n]+)\\?\\}");


    private static JdbcTemplate jdbc;
    @Autowired
    private JdbcTemplate injectedJdbcTemplate;

    /**
     * 执行 INSERT / UPDATE / DELETE 等修改语句
     */
    private static int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }

    /**
     * 执行 SELECT 查询语句，返回 List<Map> 格式结果
     */
    private static List<Map<String, Object>> query(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    // 静态初始化以便在静态方法中使用
    @PostConstruct
    public void init() {
        jdbc = this.injectedJdbcTemplate;
    }

    /**
     * 通用 SQL 执行器
     *
     * @param sql  任意 SQL 语句
     * @param args 参数数组
     * @return SELECT: 返回 List<Map<String, Object>>；其他返回 Integer（影响行数）
     */
    private static Object execute(String sql, Object... args) {
        String trimmedSql = sql.trim().toLowerCase(Locale.ROOT);
        if (trimmedSql.startsWith("select")) {
            return jdbc.queryForList(sql, args);
        } else {
            int rowsAffected = jdbc.update(sql, args);
            List<Map<String, Object>> result = new ArrayList<>();
            result.add((Map<String, Object>) new HashMap<>().put("1", rowsAffected));
            return result; // 对于 insert/update/delete/create/drop 等
        }
    }

    //新增
    public static List<JSONObject> insert(String tableName, List<JSONObject> data) {
        List<JSONObject> result = new ArrayList<>();

        for (JSONObject item : data) {
            String sql = "INSERT INTO " + tableName + " (";
            String values = " VALUES (";
            for (String key : item.keySet()) {
                sql += key + ",";
                Object val = item.get(key);

                // 处理 JSON 数组、对象类型统一转为字符串
                if (val instanceof JSONArray || val instanceof JSONObject) {
                    val = val.toString();
                }
                DramVariable.set(key + "123", val);
                values += "{?" + key + "123" + "?},";
            }
            sql = sql.substring(0, sql.length() - 1) + ")" + values;
            sql = sql.substring(0, sql.length() - 1) + ")";
            runQuery(sql);
            DramVariable.clear();
        }
        return result;
    }

    //更新
    public static List<JSONObject> update(String tableName, List<JSONObject> data) {
        List<JSONObject> result = new ArrayList<>();


        return result;
    }

    //删除
    public static List<JSONObject> delete(String tableName, List<JSONObject> data) {
        List<JSONObject> result = new ArrayList<>();

        return result;
    }

    //更新
    public static void sync(String tableName, List<JSONObject> insert, List<JSONObject> update, List<JSONObject> delete) {
        if (!insert.isEmpty()) insert(tableName, insert);
        if (!update.isEmpty()) update(tableName, insert);
        if (!delete.isEmpty()) delete(tableName, delete);
    }


    //执行SQL
    public static JSONArray runQuery(String sql) {
        List<Map<String, Object>> result = null;
        sql = dispose(sql);
        System.out.println("sql::::" + sql);
        result = (List<Map<String, Object>>) execute(sql);
        return new JSONArray(result);
    }

    //对SQL语句进行替换把{？xxx？}替换成实际值
    public static String dispose(String sql) {


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
