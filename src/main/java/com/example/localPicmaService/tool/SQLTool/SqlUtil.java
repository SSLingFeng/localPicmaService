package com.example.localPicmaService.tool.SQLTool;

import java.io.Reader;
import java.sql.*;
import java.sql.Date;
import java.util.*;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通用SQL工具类 —— 提供基于参数化占位符的查询、执行、分页和表同步能力。
 * <p>
 * 本类为纯JDBC实现，不依赖ORM框架，适用于轻量级数据库操作场景。
 * 所有公共方法均为线程安全的静态方法，内部通过 {@link DataSource} 获取连接。
 * </p>
 *
 * <h3>占位符语法</h3>
 * <p>SQL中使用 {@code {?type|name?}} 格式的参数占位符：</p>
 * <ul>
 *   <li>{@code type} — 可选，java.sql.Types 类型名（如 varchar、integer、timestamp）</li>
 *   <li>{@code name} — 参数名，对应 params Map 中的 key</li>
 *   <li>示例：{@code {?varchar|username?}}、{@code {?integer|id?}}、{@code {?name?}}</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 查询多行
 * List<Map<String, Object>> rows = SqlUtil.query(
 *     "SELECT * FROM users WHERE status = {?integer|status?}",
 *     Map.of("status", 1), 100);
 *
 * // 查询单行
 * Map<String, Object> row = SqlUtil.row(
 *     "SELECT * FROM users WHERE id = {?integer|id?}",
 *     Map.of("id", 1));
 *
 * // 分页查询（自动计算总数）
 * PageResult page = SqlUtil.queryPage(
 *     "SELECT * FROM users WHERE status = {?integer|status?}",
 *     Map.of("status", 1, "size", 10, "from", 0));
 * long total = page.total;
 * List<Map<String, Object>> items = page.items;
 *
 * // 执行更新
 * int affected = SqlUtil.exec(
 *     "UPDATE users SET name = {?varchar|name?} WHERE id = {?integer|id?}",
 *     Map.of("name", "张三", "id", 1));
 *
 * // 表同步（自动从元数据生成INSERT/UPDATE/DELETE SQL）
 * SqlUtil.sync("my_table")
 *     .insert(List.of(Map.of("field1", "ab", "field2", 12)))
 *     .update(List.of(Map.of("field1", "new", "$field1", "old")))
 *     .del(List.of(Map.of("$field1", "xyz")))
 *     .commit();
 * }</pre>
 *
 * @author localPicmaService
 * @see SqlUtilConfig Spring自动配置
 */
public class SqlUtil {

    private static final Logger log = LoggerFactory.getLogger(SqlUtil.class);

    /** 数据源，由 {@link SqlUtilConfig} 在应用启动时注入 */
    private static DataSource dataSource;

    /**
     * 设置数据源（包级访问，仅供 {@link SqlUtilConfig} 调用）。
     *
     * @param ds Spring管理的DataSource
     */
    static void setDataSource(DataSource ds) {
        dataSource = ds;
    }

    /**
     * 获取当前数据源。
     *
     * @return DataSource实例
     */
    public static DataSource getDataSource() {
        return dataSource;
    }

    // ======================== 查询方法 ========================

    /**
     * 查询多行数据。
     * <p>使用PreparedStatement参数化查询，防止SQL注入。</p>
     *
     * @param sql     SQL语句，支持 {@code {?type|name?}} 占位符
     * @param params  参数Map，key为占位符中的name部分
     * @param maxRows 最大返回行数，-1表示不限制
     * @return 行数据列表，每行为 LinkedHashMap（保持字段顺序），字段值为null时该字段不包含在Map中
     * @throws Exception SQL执行异常
     */
    public static List<Map<String, Object>> query(String sql, Map<String, Object> params, int maxRows) throws Exception {
        CompiledSQL compiled = compile(sql);
        logSql(compiled, params);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pst = conn.prepareStatement(compiled.sql)) {

            setParams(pst, compiled.params, params);
            if (maxRows > 0) pst.setMaxRows(maxRows);

            try (ResultSet rs = pst.executeQuery()) {
                return readRows(rs, maxRows);
            }
        }
    }

    /**
     * 查询全部行数据（无行数限制）。
     *
     * @param sql    SQL语句
     * @param params 参数Map
     * @return 行数据列表
     * @throws Exception SQL执行异常
     */
    public static List<Map<String, Object>> query(String sql, Map<String, Object> params) throws Exception {
        return query(sql, params, -1);
    }

    /**
     * 无参数查询全部行数据。
     *
     * @param sql SQL语句（无占位符）
     * @return 行数据列表
     * @throws Exception SQL执行异常
     */
    public static List<Map<String, Object>> query(String sql) throws Exception {
        return query(sql, null, -1);
    }

    /**
     * 查询单行数据。
     * <p>内部限制 maxRows=1，返回结果集的第一行。</p>
     *
     * @param sql    SQL语句
     * @param params 参数Map
     * @return 第一行数据Map，无数据返回 null
     * @throws Exception SQL执行异常
     */
    public static Map<String, Object> row(String sql, Map<String, Object> params) throws Exception {
        List<Map<String, Object>> rows = query(sql, params, 1);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 无参数查询单行数据。
     *
     * @param sql SQL语句
     * @return 第一行数据Map，无数据返回 null
     * @throws Exception SQL执行异常
     */
    public static Map<String, Object> row(String sql) throws Exception {
        return row(sql, null);
    }

    /**
     * 分页查询：返回指定范围的数据行，同时返回总条目数。
     * <p>
     * 分页参数从 params 中读取并自动移除（不会传入SQL）：
     * </p>
     * <ul>
     *   <li>{@code size} — 每页条数（对应 LIMIT）</li>
     *   <li>{@code from} — 起始偏移，0-based（对应 OFFSET，优先级高于 to）</li>
     *   <li>{@code to}   — 结束位置（可选，当未指定 from 时根据 size 计算 offset）</li>
     * </ul>
     * <p>
     * 实现原理：先执行 {@code SELECT COUNT(*) FROM (原SQL) _t} 获取总数，
     * 再拼接 {@code LIMIT ... OFFSET ...} 查询分页数据。
     * </p>
     *
     * @param sql    查询SQL
     * @param params 参数Map，可包含 size、from、to 分页参数及其他业务参数
     * @return {@link PageResult} 包含 items（数据行）、total（总条目数）、page（当前页码）、pageSize（每页条数）
     * @throws Exception SQL执行异常
     */
    public static PageResult queryPage(String sql, Map<String, Object> params) throws Exception {
        Map<String, Object> safeParams = params != null ? new HashMap<>(params) : new HashMap<>();

        // 提取分页参数并从params中移除（避免干扰SQL编译）
        Integer size = extractInt(safeParams, "size");
        Integer from = extractInt(safeParams, "from");
        Integer to   = extractInt(safeParams, "to");

        // 计算 LIMIT 和 OFFSET
        int limit = -1;
        int offset = -1;
        if (size != null && size > 0) {
            limit = size;
            if (from != null && from >= 0) {
                offset = from;
            } else if (to != null && to >= 0) {
                offset = to - size + 1;
                if (offset < 0) offset = 0;
            }
        }

        // 1. 查询总数
        String countSql = "SELECT COUNT(*) AS cnt FROM (" + sql + ") _t";
        Map<String, Object> countRow = row(countSql, safeParams);
        long total = 0;
        if (countRow != null) {
            Object cnt = countRow.values().iterator().next();
            if (cnt instanceof Number) total = ((Number) cnt).longValue();
        }

        // 2. 查询分页数据
        String pageSql = sql;
        if (limit > 0) {
            pageSql = sql + " LIMIT " + limit;
            if (offset >= 0) {
                pageSql += " OFFSET " + offset;
            }
        }

        List<Map<String, Object>> items = query(pageSql, safeParams, -1);

        PageResult result = new PageResult();
        result.items = items;
        result.total = total;
        result.page = (limit > 0 && offset >= 0) ? (offset / limit + 1) : 1;
        result.pageSize = limit > 0 ? limit : items.size();
        return result;
    }

    /**
     * 分页查询结果对象。
     * <p>包含当前页数据、总条目数、当前页码和每页条数。</p>
     */
    public static class PageResult {
        /** 当前页数据行列表 */
        public List<Map<String, Object>> items;
        /** 满足查询条件的总条目数 */
        public long total;
        /** 当前页码（1-based） */
        public int page;
        /** 每页条数 */
        public int pageSize;
    }

    /**
     * 从Map中提取Integer值并移除该key。
     * <p>用于安全提取分页参数，避免其作为业务参数传入SQL。</p>
     */
    private static Integer extractInt(Map<String, Object> map, String key) {
        Object val = map.remove(key);
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).intValue();
        try { return Integer.parseInt(val.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    // ======================== 执行方法 ========================

    /**
     * 执行写操作SQL（INSERT / UPDATE / DELETE / DDL）。
     * <p>使用PreparedStatement参数化执行，防止SQL注入。</p>
     *
     * @param sql    SQL语句，支持 {@code {?type|name?}} 占位符
     * @param params 参数Map
     * @return 受影响的行数
     * @throws Exception SQL执行异常
     */
    public static int exec(String sql, Map<String, Object> params) throws Exception {
        CompiledSQL compiled = compile(sql);
        logSql(compiled, params);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pst = conn.prepareStatement(compiled.sql)) {

            setParams(pst, compiled.params, params);
            return pst.executeUpdate();
        }
    }

    /**
     * 无参数执行写操作SQL。
     *
     * @param sql SQL语句
     * @return 受影响的行数
     * @throws Exception SQL执行异常
     */
    public static int exec(String sql) throws Exception {
        return exec(sql, null);
    }

    // ======================== 表同步方法 ========================

    /**
     * 创建表同步构建器。
     * <p>
     * 自动从数据库表元数据生成 SELECT / INSERT / UPDATE / DELETE 语句。
     * 数据中以 {@code $} 开头的字段名（如 {@code $id}）用作 WHERE 条件的原值。
     * </p>
     *
     * @param tableName 目标表名
     * @return {@link SyncBuilder} 链式构建器
     */
    public static SyncBuilder sync(String tableName) {
        return new SyncBuilder(tableName);
    }

    /**
     * 表同步构建器 —— 支持链式调用的INSERT / UPDATE / DELETE操作。
     * <p>
     * 所有操作在同一事务中执行，调用 {@link #commit()} 提交。
     * 事务中任一操作失败会自动回滚。
     * </p>
     *
     * <h3>使用示例</h3>
     * <pre>{@code
     * // 批量插入 + 更新 + 删除
     * SqlUtil.sync("my_table")
     *     .insert(List.of(Map.of("name", "张三", "age", 25)))
     *     .update(List.of(Map.of("name", "李四", "$name", "张三")))  // $name 用于WHERE匹配
     *     .del(List.of(Map.of("$name", "王五")))                     // $name 用于WHERE匹配
     *     .commit();
     *
     * // 仅插入
     * SqlUtil.sync("my_table").insert(dataList).commit();
     * }</pre>
     */
    public static class SyncBuilder {
        private final String tableName;
        private List<Map<String, Object>> insertData;
        private List<Map<String, Object>> updateData;
        private List<Map<String, Object>> deleteData;
        private Map<String, Object> selectParams;
        private boolean autoCommit = true;

        SyncBuilder(String tableName) {
            this.tableName = tableName;
        }

        /**
         * 设置待插入的数据列表。
         *
         * @param data 每个Map为一行，key为字段名，value为字段值
         * @return 当前构建器（链式调用）
         */
        public SyncBuilder insert(List<Map<String, Object>> data) {
            this.insertData = data;
            return this;
        }

        /**
         * 设置待更新的数据列表。
         * <p>数据中以 {@code $} 开头的字段名用作WHERE条件的原值。</p>
         *
         * @param data 每个Map为一行，{@code $field} 表示WHERE条件值
         * @return 当前构建器
         */
        public SyncBuilder update(List<Map<String, Object>> data) {
            this.updateData = data;
            return this;
        }

        /**
         * 设置待删除的数据列表。
         * <p>数据中以 {@code $} 开头的字段名用作WHERE条件。</p>
         *
         * @param data 每个Map为一行
         * @return 当前构建器
         */
        public SyncBuilder del(List<Map<String, Object>> data) {
            this.deleteData = data;
            return this;
        }

        /**
         * 设置查询参数，提交时会同时执行SELECT查询并返回结果。
         *
         * @param params WHERE条件参数
         * @return 当前构建器
         */
        public SyncBuilder select(Map<String, Object> params) {
            this.selectParams = params;
            return this;
        }

        /**
         * 设置是否自动提交事务。默认为 true。
         *
         * @param autoCommit true=commit，false=需手动提交
         * @return 当前构建器
         */
        public SyncBuilder autoCommit(boolean autoCommit) {
            this.autoCommit = autoCommit;
            return this;
        }

        /**
         * 执行同步操作（提交事务）。
         * <p>
         * 执行顺序：DELETE → UPDATE → INSERT → SELECT（如有）。
         * 涉及写操作时自动开启事务，失败自动回滚。
         * </p>
         *
         * @return 如果设置了 selectParams，返回查询结果；否则返回 null
         * @throws Exception SQL执行异常或非唯一更新异常
         */
        public List<Map<String, Object>> commit() throws Exception {
            boolean hasInsert = insertData != null && !insertData.isEmpty();
            boolean hasUpdate = updateData != null && !updateData.isEmpty();
            boolean hasDel = deleteData != null && !deleteData.isEmpty();
            boolean needUpdate = hasInsert || hasUpdate || hasDel;

            TableSQL sql = null;
            if (needUpdate || selectParams != null) {
                sql = buildTableSQL(tableName);
            }

            try (Connection conn = dataSource.getConnection()) {
                boolean origAutoCommit = conn.getAutoCommit();
                if (needUpdate) conn.setAutoCommit(false);

                try {
                    if (hasDel) executeBatch(conn, sql.del, deleteData, true);
                    if (hasUpdate) executeBatch(conn, sql.update, updateData, true);
                    if (hasInsert) executeBatch(conn, sql.insert, insertData, false);

                    if (selectParams != null) {
                        List<Map<String, Object>> result;
                        try (PreparedStatement pst = conn.prepareStatement(sql.select)) {
                            CompiledSQL cs = compile(sql.select);
                            setParams(pst, cs.params, selectParams);
                            try (ResultSet rs = pst.executeQuery()) {
                                result = readRows(rs, -1);
                            }
                        }
                        if (autoCommit && needUpdate) conn.commit();
                        return result;
                    }

                    if (autoCommit && needUpdate) conn.commit();
                } catch (Exception e) {
                    if (needUpdate) conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(origAutoCommit);
                }
            }
            return null;
        }
    }

    // ======================== SQL编译与参数绑定 ========================

    /** 编译后的SQL及其参数元数据 */
    private static class CompiledSQL {
        /** 替换占位符为 ? 后的可执行SQL */
        final String sql;
        /** 参数元数据列表（与 ? 一一对应） */
        final List<ParamMeta> params;

        CompiledSQL(String sql, List<ParamMeta> params) {
            this.sql = sql;
            this.params = params;
        }
    }

    /** 单个参数的元数据 */
    private static class ParamMeta {
        /** java.sql.Types 常量，null 表示由 setObject 自动判断 */
        final Integer type;
        /** 参数名（对应 params Map 的 key） */
        final String name;

        ParamMeta(Integer type, String name) {
            this.type = type;
            this.name = name;
        }
    }

    /** 表自动生成的SQL集合（SELECT / INSERT / UPDATE / DELETE） */
    private static class TableSQL {
        String select, insert, update, del;
    }

    /**
     * 编译SQL：将 {@code {?type|name?}} 占位符替换为 PreparedStatement 的 ? 占位符。
     * <p>
     * 解析过程：
     * <ol>
     *   <li>扫描SQL中的 {@code {...}} 格式占位符</li>
     *   <li>跳过字符串字面量内的内容（单引号/双引号包裹）</li>
     *   <li>提取类型名和参数名，生成对应的 ParamMeta</li>
     *   <li>将占位符替换为 ?</li>
     * </ol>
     * </p>
     *
     * @param sql 原始SQL（含自定义占位符）
     * @return 编译结果（标准SQL + 参数元数据列表）
     */
    private static CompiledSQL compile(String sql) {
        List<ParamMeta> params = new ArrayList<>();
        StringBuilder result = new StringBuilder();
        int lastPos = 0;
        int pos = 0;

        while ((pos = sql.indexOf('{', pos)) > -1) {
            if (pos + 2 >= sql.length()) break;
            char marker = sql.charAt(pos + 1);
            if (marker != '?' && marker != '*') { pos++; continue; }

            int end = sql.indexOf(marker + "}", pos + 2);
            if (end == -1) break;

            result.append(sql, lastPos, pos);
            String inner = sql.substring(pos + 2, end);
            String[] parts = inner.split("\\|");

            String paramName = parts[parts.length - 1].trim();
            Integer paramType = parts.length > 1 ? getSqlType(parts[0].trim()) : null;

            params.add(new ParamMeta(paramType, paramName));
            result.append('?');
            pos = lastPos = end + 2;
        }
        result.append(sql.substring(lastPos));
        return new CompiledSQL(result.toString(), params);
    }

    /**
     * 输出SQL日志：将编译后的SQL与参数值拼接为可读形式。
     * <p>仅在 DEBUG 级别启用时执行字符串拼接，避免性能损耗。</p>
     */
    private static void logSql(CompiledSQL compiled, Map<String, Object> params) {
        if (!log.isDebugEnabled()) return;
        if (compiled.params.isEmpty()) {
            log.debug("[SQL] {}", compiled.sql);
            return;
        }
        StringBuilder sb = new StringBuilder(compiled.sql);
        sb.append(" | params: [");
        for (int i = 0; i < compiled.params.size(); i++) {
            if (i > 0) sb.append(", ");
            ParamMeta meta = compiled.params.get(i);
            Object val = params != null ? params.get(meta.name) : null;
            sb.append(meta.name).append("=");
            if (val == null) {
                sb.append("null");
            } else if (val instanceof String) {
                sb.append("'").append(val).append("'");
            } else {
                sb.append(val);
            }
        }
        sb.append("]");
        log.debug("[SQL] {}", sb);
    }

    /**
     * 将参数值绑定到 PreparedStatement。
     * <p>按编译时提取的参数顺序依次设置，null 或空字符串设置为 SQL NULL。</p>
     *
     * @param pst    PreparedStatement实例
     * @param metas  参数元数据列表
     * @param params 参数值Map
     */
    private static void setParams(PreparedStatement pst, List<ParamMeta> metas,
                                   Map<String, Object> params) throws Exception {
        for (int i = 0; i < metas.size(); i++) {
            ParamMeta meta = metas.get(i);
            Object value = params != null ? params.get(meta.name) : null;
            setObject(pst, i + 1, value, meta.type);
        }
    }

    /**
     * 按SQL类型设置 PreparedStatement 的单个参数值。
     * <p>
     * 当指定了类型时，使用对应的 setType 方法以获得最佳类型匹配；
     * 未指定类型时，通过 setObject 自动判断。
     * </p>
     *
     * @param pst   PreparedStatement实例
     * @param index 参数索引（1-based）
     * @param value 参数值
     * @param type  java.sql.Types 常量，null 表示自动判断
     */
    private static void setObject(PreparedStatement pst, int index, Object value, Integer type) throws Exception {
        if (value == null || "".equals(value)) {
            pst.setNull(index, type != null ? type : Types.VARCHAR);
            return;
        }

        if (type != null) {
            switch (type) {
                case Types.CHAR: case Types.VARCHAR:
                    pst.setString(index, value.toString()); return;
                case Types.NCHAR: case Types.NVARCHAR:
                    pst.setNString(index, value.toString()); return;
                case Types.INTEGER:
                    pst.setInt(index, toInt(value)); return;
                case Types.TINYINT:
                    pst.setByte(index, toByte(value)); return;
                case Types.SMALLINT:
                    pst.setShort(index, toShort(value)); return;
                case Types.BIGINT:
                    pst.setLong(index, toLong(value)); return;
                case Types.FLOAT: case Types.REAL:
                    pst.setFloat(index, toFloat(value)); return;
                case Types.DOUBLE: case Types.DECIMAL: case Types.NUMERIC:
                    pst.setDouble(index, toDouble(value)); return;
                case Types.TIMESTAMP:
                    pst.setTimestamp(index, new Timestamp(toDate(value).getTime())); return;
                case Types.DATE:
                    pst.setDate(index, new Date(toDate(value).getTime())); return;
                case Types.TIME:
                    pst.setTime(index, new Time(toDate(value).getTime())); return;
                case Types.BOOLEAN: case Types.BIT:
                    pst.setBoolean(index, toBool(value)); return;
                case Types.BLOB: case Types.BINARY: case Types.VARBINARY:
                    if (value instanceof byte[]) pst.setBytes(index, (byte[]) value);
                    else pst.setBytes(index, value.toString().getBytes());
                    return;
            }
        }

        // 无类型时自动判断
        if (value instanceof java.util.Date) {
            pst.setTimestamp(index, new Timestamp(((java.util.Date) value).getTime()));
        } else if (value instanceof byte[]) {
            pst.setBytes(index, (byte[]) value);
        } else {
            pst.setObject(index, value);
        }
    }

    // ======================== ResultSet 读取 ========================

    /**
     * 从 ResultSet 读取多行数据。
     * <p>
     * 每行数据为 LinkedHashMap（保持字段声明顺序）。
     * 字段值为 null 时仍会包含在 Map 中（value 为 null）。
     * </p>
     *
     * @param rs      ResultSet实例
     * @param maxRows 最大读取行数，-1 表示不限制
     * @return 行数据列表
     */
    private static List<Map<String, Object>> readRows(ResultSet rs, int maxRows) throws Exception {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();
        int[] types = new int[colCount];
        String[] names = new String[colCount];

        for (int i = 0; i < colCount; i++) {
            types[i] = meta.getColumnType(i + 1);
            names[i] = meta.getColumnLabel(i + 1);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int count = 0;
        while (rs.next()) {
            if (maxRows > 0 && count >= maxRows) break;
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < colCount; i++) {
                Object val = readValue(rs, i + 1, types[i]);
                row.put(names[i], val);
            }
            rows.add(row);
            count++;
        }
        return rows;
    }

    /**
     * 从 ResultSet 读取单个字段值，按SQL类型选择最优的读取方式。
     * <p>读取完成后通过 {@code rs.wasNull()} 判断是否为SQL NULL。</p>
     *
     * @param rs    ResultSet实例
     * @param index 字段索引（1-based）
     * @param type  字段的 java.sql.Types 类型
     * @return 字段值，SQL NULL 返回 null
     */
    private static Object readValue(ResultSet rs, int index, int type) throws Exception {
        Object value;
        switch (type) {
            case Types.CHAR: case Types.VARCHAR:
                value = rs.getString(index); break;
            case Types.NCHAR: case Types.NVARCHAR:
                value = rs.getNString(index); break;
            case Types.INTEGER:
                value = rs.getInt(index); break;
            case Types.TINYINT:
                value = (int) rs.getByte(index); break;
            case Types.SMALLINT:
                value = (int) rs.getShort(index); break;
            case Types.BIGINT:
                value = rs.getLong(index); break;
            case Types.FLOAT: case Types.REAL:
                value = rs.getFloat(index); break;
            case Types.DOUBLE: case Types.DECIMAL: case Types.NUMERIC:
                value = rs.getDouble(index); break;
            case Types.TIMESTAMP:
                Timestamp ts = rs.getTimestamp(index);
                value = ts != null ? new java.util.Date(ts.getTime()) : null; break;
            case Types.DATE:
                Date d = rs.getDate(index);
                value = d != null ? new java.util.Date(d.getTime()) : null; break;
            case Types.TIME:
                Time t = rs.getTime(index);
                value = t != null ? new java.util.Date(t.getTime()) : null; break;
            case Types.BOOLEAN: case Types.BIT:
                value = rs.getBoolean(index) ? 1 : 0; break;
            case Types.CLOB: case Types.NCLOB:
                value = readClob(rs, index, type); break;
            case Types.LONGVARCHAR:
                value = readText(rs.getCharacterStream(index)); break;
            case Types.LONGNVARCHAR:
                value = readText(rs.getNCharacterStream(index)); break;
            case Types.BLOB: case Types.BINARY: case Types.VARBINARY: case Types.LONGVARBINARY:
                value = "(blob)"; break;
            default:
                value = rs.getObject(index); break;
        }
        return rs.wasNull() ? null : value;
    }

    /** 读取 CLOB / NCLOB 内容为字符串 */
    private static String readClob(ResultSet rs, int index, int type) throws Exception {
        Clob clob = type == Types.NCLOB ? rs.getNClob(index) : rs.getClob(index);
        if (clob == null) return null;
        return readText(clob.getCharacterStream());
    }

    /** 从 Reader 读取全部文本内容并关闭 Reader */
    private static String readText(Reader reader) throws Exception {
        if (reader == null) return null;
        try {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int len;
            while ((len = reader.read(buf)) != -1) sb.append(buf, 0, len);
            return sb.toString();
        } finally {
            reader.close();
        }
    }

    // ======================== 表SQL自动生成 ========================

    /**
     * 根据表元数据自动生成 SELECT / INSERT / UPDATE / DELETE 语句。
     * <p>
     * 生成规则：
     * <ul>
     *   <li>WHERE 条件优先使用主键字段</li>
     *   <li>无主键时使用所有适合做WHERE条件的字段（数值/日期/短字符串）</li>
     *   <li>INSERT 和 UPDATE 排除只读字段</li>
     * </ul>
     * </p>
     *
     * @param tableName 表名
     * @return 包含四种SQL的对象
     */
    private static TableSQL buildTableSQL(String tableName) throws Exception {
        TableSQL result = new TableSQL();
        try (Connection conn = dataSource.getConnection()) {
            List<String> pkFields = getPkFields(conn, tableName);
            boolean hasPk = pkFields != null && !pkFields.isEmpty();

            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("SELECT * FROM " + tableName + " WHERE 1=0");
            try {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();

                StringBuilder selectF = new StringBuilder();
                StringBuilder insertF = new StringBuilder();
                StringBuilder insertV = new StringBuilder();
                StringBuilder updateS = new StringBuilder();
                StringBuilder where = new StringBuilder();
                boolean selComma = false, updComma = false;

                for (int i = 1; i <= colCount; i++) {
                    String field = meta.getColumnLabel(i);
                    int type = meta.getColumnType(i);
                    boolean isPk = hasPk && pkFields.contains(field.toLowerCase());

                    if (selComma) selectF.append(",");
                    else selComma = true;
                    selectF.append(field);

                    if (!meta.isReadOnly(i)) {
                        if (updComma) { insertF.append(","); insertV.append(","); updateS.append(","); }
                        else updComma = true;

                        String param = "{?" + getTypeName(type) + "|" + field + "?}";
                        insertF.append(field);
                        insertV.append(param);
                        updateS.append(field).append("=").append(param);
                    }

                    if (isPk) {
                        if (where.length() > 0) where.append(" AND ");
                        where.append(field).append("={?").append(getTypeName(type)).append("|$").append(field).append("?}");
                    }
                }

                if (where.length() == 0) {
                    for (int i = 1; i <= colCount; i++) {
                        int type = meta.getColumnType(i);
                        if (isWhereType(type)) {
                            String field = meta.getColumnLabel(i);
                            if (where.length() > 0) where.append(" AND ");
                            where.append(field).append("={?").append(getTypeName(type)).append("|$").append(field).append("?}");
                        }
                    }
                }

                String w = where.toString();
                result.select = "SELECT " + selectF + " FROM " + tableName + " WHERE " + w;
                result.insert = "INSERT INTO " + tableName + " (" + insertF + ") VALUES (" + insertV + ")";
                result.update = "UPDATE " + tableName + " SET " + updateS + " WHERE " + w;
                result.del = "DELETE FROM " + tableName + " WHERE " + w;
            } finally {
                rs.close();
                st.close();
            }
        }
        return result;
    }

    /**
     * 获取表的主键字段列表（返回小写字段名）。
     *
     * @param conn      数据库连接
     * @param tableName 表名（可含 schema，以 . 分隔）
     * @return 主键字段名列表，无主键返回空列表
     */
    private static List<String> getPkFields(Connection conn, String tableName) throws Exception {
        DatabaseMetaData meta = conn.getMetaData();
        String schema = null, table = tableName;
        int dot = tableName.indexOf('.');
        if (dot >= 0) { schema = tableName.substring(0, dot); table = tableName.substring(dot + 1); }

        ResultSet rs = meta.getPrimaryKeys(null, schema, table);
        List<String> pks = new ArrayList<>();
        try {
            while (rs.next()) {
                String col = rs.getString("COLUMN_NAME");
                if (col != null) pks.add(col.toLowerCase());
            }
        } finally { rs.close(); }
        return pks;
    }

    /**
     * 批量执行SQL（用于 sync 操作中的 INSERT / UPDATE / DELETE）。
     *
     * @param conn     数据库连接（需已开启事务）
     * @param sql      带占位符的SQL
     * @param dataList 参数列表，每行为一组参数
     * @param unique   是否强制唯一（true时每条SQL影响行数必须为1）
     */
    private static void executeBatch(Connection conn, String sql,
                                      List<Map<String, Object>> dataList, boolean unique) throws Exception {
        CompiledSQL compiled = compile(sql);
        log.debug("[SQL] {} | batch size: {}", compiled.sql, dataList.size());
        try (PreparedStatement pst = conn.prepareStatement(compiled.sql)) {
            for (Map<String, Object> params : dataList) {
                setParams(pst, compiled.params, params);
                pst.addBatch();
            }
            int[] results = pst.executeBatch();
            if (unique) {
                for (int r : results) {
                    if (r != 1 && r != -2) {
                        throw new RuntimeException("Non-unique update: affected " + r + " rows.");
                    }
                }
            }
        }
    }

    // ======================== SQL类型映射 ========================

    /** 类型名 → java.sql.Types 映射表 */
    private static final Map<String, Integer> TYPE_MAP = new HashMap<>();
    static {
        TYPE_MAP.put("bit", Types.BIT);
        TYPE_MAP.put("tinyint", Types.TINYINT);
        TYPE_MAP.put("smallint", Types.SMALLINT);
        TYPE_MAP.put("integer", Types.INTEGER);
        TYPE_MAP.put("int", Types.INTEGER);
        TYPE_MAP.put("bigint", Types.BIGINT);
        TYPE_MAP.put("float", Types.FLOAT);
        TYPE_MAP.put("real", Types.REAL);
        TYPE_MAP.put("double", Types.DOUBLE);
        TYPE_MAP.put("numeric", Types.NUMERIC);
        TYPE_MAP.put("decimal", Types.DECIMAL);
        TYPE_MAP.put("char", Types.CHAR);
        TYPE_MAP.put("varchar", Types.VARCHAR);
        TYPE_MAP.put("longvarchar", Types.LONGVARCHAR);
        TYPE_MAP.put("date", Types.DATE);
        TYPE_MAP.put("time", Types.TIME);
        TYPE_MAP.put("timestamp", Types.TIMESTAMP);
        TYPE_MAP.put("binary", Types.BINARY);
        TYPE_MAP.put("varbinary", Types.VARBINARY);
        TYPE_MAP.put("longvarbinary", Types.LONGVARBINARY);
        TYPE_MAP.put("boolean", Types.BOOLEAN);
        TYPE_MAP.put("nchar", Types.NCHAR);
        TYPE_MAP.put("nvarchar", Types.NVARCHAR);
        TYPE_MAP.put("longnvarchar", Types.LONGNVARCHAR);
        TYPE_MAP.put("clob", Types.CLOB);
        TYPE_MAP.put("nclob", Types.NCLOB);
        TYPE_MAP.put("blob", Types.BLOB);
    }

    /** java.sql.Types → 类型名 反向映射表 */
    private static final Map<Integer, String> NAME_MAP = new HashMap<>();
    static {
        TYPE_MAP.forEach((name, type) -> NAME_MAP.putIfAbsent(type, name));
    }

    /** 将类型名字符串转为 java.sql.Types 常量 */
    private static Integer getSqlType(String name) {
        Integer t = TYPE_MAP.get(name.toLowerCase());
        if (t != null) return t;
        try { return Integer.parseInt(name); }
        catch (NumberFormatException e) { throw new RuntimeException("Unknown SQL type: " + name); }
    }

    /** 将 java.sql.Types 常量转为小写类型名 */
    private static String getTypeName(int type) {
        return NAME_MAP.getOrDefault(type, String.valueOf(type));
    }

    /** 判断字段类型是否适合用作 WHERE 条件（排除大文本/二进制类型） */
    private static boolean isWhereType(int type) {
        switch (type) {
            case Types.BIT: case Types.TINYINT: case Types.SMALLINT:
            case Types.INTEGER: case Types.BIGINT: case Types.BOOLEAN:
            case Types.FLOAT: case Types.REAL: case Types.DOUBLE:
            case Types.DECIMAL: case Types.NUMERIC:
            case Types.DATE: case Types.TIME: case Types.TIMESTAMP:
                return true;
            case Types.CHAR: case Types.NCHAR:
            case Types.VARCHAR: case Types.NVARCHAR:
            case Types.LONGVARCHAR: case Types.LONGNVARCHAR:
                return true;
            default:
                return false;
        }
    }

    // ======================== 类型转换工具 ========================

    private static int toInt(Object v) {
        if (v instanceof Number) return ((Number) v).intValue();
        return Integer.parseInt(v.toString());
    }
    private static byte toByte(Object v) {
        if (v instanceof Number) return ((Number) v).byteValue();
        return Byte.parseByte(v.toString());
    }
    private static short toShort(Object v) {
        if (v instanceof Number) return ((Number) v).shortValue();
        return Short.parseShort(v.toString());
    }
    private static long toLong(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        return Long.parseLong(v.toString());
    }
    private static float toFloat(Object v) {
        if (v instanceof Number) return ((Number) v).floatValue();
        return Float.parseFloat(v.toString());
    }
    private static double toDouble(Object v) {
        if (v instanceof Number) return ((Number) v).doubleValue();
        return Double.parseDouble(v.toString());
    }
    private static boolean toBool(Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).intValue() != 0;
        return "true".equalsIgnoreCase(v.toString());
    }
    private static java.util.Date toDate(Object v) {
        if (v instanceof java.util.Date) return (java.util.Date) v;
        if (v instanceof Long) return new java.util.Date((Long) v);
        if (v instanceof String) {
            for (String fmt : new String[]{"yyyy-MM-dd HH:mm:ss","yyyy-MM-dd","yyyy/MM/dd HH:mm:ss","yyyy/MM/dd"}) {
                try { return new java.text.SimpleDateFormat(fmt).parse((String) v); }
                catch (java.text.ParseException ignored) {}
            }
        }
        return new java.util.Date();
    }
}
