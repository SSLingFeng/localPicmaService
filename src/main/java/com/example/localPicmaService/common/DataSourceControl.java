package com.example.localPicmaService.common;

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

    private static int update(String sql, Object... args) {
        return jdbc.update(sql, args);
    }

    private static List<Map<String, Object>> query(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    @PostConstruct
    public void init() {
        jdbc = this.injectedJdbcTemplate;
    }

    private static Object execute(String sql, Object... args) {
        String trimmedSql = sql.trim().toLowerCase(Locale.ROOT);
        if (trimmedSql.startsWith("select")) {
            return jdbc.queryForList(sql, args);
        } else {
            int rowsAffected = jdbc.update(sql, args);
            List<Map<String, Object>> result = new ArrayList<>();
            result.add((Map<String, Object>) new HashMap<>().put("1", rowsAffected));
            return result;
        }
    }

    public static List<JSONObject> insert(String tableName, List<JSONObject> data) {
        List<JSONObject> result = new ArrayList<>();
        for (JSONObject item : data) {
            String sql = "INSERT INTO " + tableName + " (";
            String values = " VALUES (";
            for (String key : item.keySet()) {
                sql += key + ",";
                Object val = item.get(key);
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

    public static List<JSONObject> update(String tableName, List<JSONObject> data) {
        return new ArrayList<>();
    }

    public static List<JSONObject> delete(String tableName, List<JSONObject> data) {
        return new ArrayList<>();
    }

    public static void sync(String tableName, List<JSONObject> insert, List<JSONObject> update, List<JSONObject> delete) {
        if (!insert.isEmpty()) insert(tableName, insert);
        if (!update.isEmpty()) update(tableName, insert);
        if (!delete.isEmpty()) delete(tableName, delete);
    }

    public static JSONArray runQuery(String sql) {
        sql = dispose(sql);
        System.out.println("sql::::" + sql);
        List<Map<String, Object>> result = (List<Map<String, Object>>) execute(sql);
        return new JSONArray(result);
    }

    public static String dispose(String sql) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(sql);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object rawValue = DramVariable.get(key);
            String safeValue = convertToSqlLiteral(rawValue);
            matcher.appendReplacement(result, Matcher.quoteReplacement(safeValue));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String convertToSqlLiteral(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        String str = value.toString();
        String escaped = str.replace("'", "''");
        return "'" + escaped + "'";
    }
}
