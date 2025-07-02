package com.example.localPicmaService.sqlTool;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class MysqlTool {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 执行更新语句（适用于INSERT、UPDATE、DELETE）
    public int executeUpdate(String sql, Object... args) {
        return jdbcTemplate.update(sql, args);
    }

    // 执行查询，返回单个对象
    public <T> T executeQueryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
        return jdbcTemplate.queryForObject(sql, rowMapper, args);
    }

    // 执行查询，返回列表
    public <T> List<T> executeQueryForList(String sql, RowMapper<T> rowMapper, Object... args) {
        return jdbcTemplate.query(sql, rowMapper, args);
    }

    // 执行查询，返回Map列表（适用于通用查询，列名作为键）
    public List<Map<String, Object>> executeQueryForMapList(String sql, Object... args) {
        return jdbcTemplate.queryForList(sql, args);
    }
}
