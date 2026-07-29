package com.example.localPicmaService.tool.SQLTool;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * SqlUtil 的 Spring 自动配置类。
 * <p>
 * 应用启动时自动将 Spring 管理的 {@link DataSource} 注入到 {@link SqlUtil}，
 * 使 SqlUtil 的所有静态方法可直接使用，无需手动传入连接。
 * </p>
 *
 * <h3>工作原理</h3>
 * <ol>
 *   <li>Spring 容器初始化时创建本 Bean</li>
 *   <li>{@link PostConstruct} 回调中将 DataSource 传给 SqlUtil</li>
 *   <li>SqlUtil 内部通过 {@code dataSource.getConnection()} 获取连接（底层由连接池管理）</li>
 * </ol>
 *
 * <h3>使用方式</h3>
 * <p>无需手动注入本类，只需在项目中引入本包，Spring 会自动扫描并执行初始化。
 * 之后在任意位置直接调用 {@code SqlUtil.query(...)} 等静态方法即可。</p>
 *
 * @author localPicmaService
 * @see SqlUtil SQL工具主类
 */
@Component
public class SqlUtilConfig {

    /** Spring 自动注入的数据源（通常为 HikariCP 连接池） */
    @Autowired
    private DataSource dataSource;

    /**
     * 应用启动时初始化 SqlUtil 的数据源。
     * <p>此方法由 Spring 容器在 Bean 创建后自动调用，无需手动触发。</p>
     */
    @PostConstruct
    public void init() {
        SqlUtil.setDataSource(dataSource);
    }
}
