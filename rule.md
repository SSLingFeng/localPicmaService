# localPicmaService — 项目规则文档

## 项目简介

localPicmaService 是一个基于 **Spring Boot 4 + Java 21 + PostgreSQL** 的本地多功能服务端，主要用途：

- **漫画资源管理**：漫画/Coser 资源的浏览、搜索、章节阅读
- **个人网站首页**：游戏日志、摄影展示、生活碎片、工作轨迹四大模块
- **后台管理**：用户、角色、菜单、战队管理
- **文件管理**：RustFS 对象存储 + Valkey 缓存
- **工具测试**：RustFS 文件上传/下载、Valkey 键值操作

---

## 技术栈

| 维度 | 技术 |
|------|------|
| 框架 | Spring Boot 4.0.1 (Jakarta EE) |
| Java | 21 |
| 数据库 | PostgreSQL (HikariCP 连接池) |
| 缓存 | Valkey / Redis (spring-data-redis) |
| 对象存储 | RustFS (S3 协议兼容, AWS SDK v2) |
| 安全 | Spring Security + JWT (Hutool JWT) |
| 密码加密 | BCrypt |
| 工具库 | Hutool 5.8.32, Lombok |
| 实时通信 | WebSocket + SSE |
| 前端框架 | Vue 3 + Element Plus 2.14.4 |
| 前端构建 | 无构建工具，原生 HTML/CSS/JS + CDN/本地 lib |
| HTTP 客户端 | Axios |

---

## 前端依赖（本地 lib）

所有前端依赖已下载到 `static/lib/` 目录，避免 CDN 不稳定：

| 文件 | 说明 |
|------|------|
| `vue.global.prod.js` | Vue 3.5.41 生产版 |
| `element-plus@2.14.4_index.css` | Element Plus 样式 |
| `element-plus@2.14.4_index.full.js` | Element Plus 完整版（含所有组件） |

---

## 项目结构

```
src/main/java/com/example/localPicmaService/
├── config/                          # 配置类
│   ├── SystemConfig.java            # 系统配置（JSON 文件加载）
│   ├── PasswordConfig.java          # 密码编码器
│   └── ErrorPageConfig.java         # 自定义错误页配置
├── security/                        # 安全模块
│   ├── SecurityConfig.java          # 安全过滤链配置
│   ├── JwtFilter.java               # JWT 过滤器
│   └── CustomUserDetailsService.java
├── page/                            # 页面 API（被 HTML 页面调用）
│   ├── auth/controller/             # 登录、注册、用户信息
│   ├── admin/controller/            # 后台管理（角色、菜单、用户）
│   ├── squad/controller/            # 战队管理
│   ├── home/                        # 首页数据 + 首页内容管理
│   ├── cartoon/controller/          # 漫画管理
│   ├── rustfs/controller/           # RustFS 文件管理
│   ├── valkey/controller/           # Valkey 键值管理
│   ├── upload/                      # 统一文件上传（支持分片）
│   ├── proxy/                       # 文件代理（公开/受保护）
│   ├── login/                       # 页面路由
│   └── ResourceController.java      # 静态资源服务
├── api/                             # 纯 API（不被 HTML 页面使用）
│   ├── comic/                       # PicACG 漫画源客户端
│   └── command/                     # 进程管理（游戏服务器等）
├── common/                          # 通用组件
│   ├── ZMessage.java                # 统一响应体
│   └── DramVariable.java            # 线程局部变量
└── tool/                            # 工具类
    ├── SQLTool/                     # 自研 SQL 工具
    │   ├── SqlUtil.java             # 通用 SQL 工具类
    │   └── SqlUtilConfig.java       # Spring 自动配置
    ├── RustFs/                      # RustFS 文件管理工具
    │   ├── RustFsConfig.java        # S3 客户端配置
    │   └── RustFsUtil.java          # 上传/下载/删除
    └── Valkey/                      # Valkey 键值工具
        └── ValkeyUtil.java          # get/set/del 等操作

src/main/resources/
├── application.yml                  # Spring Boot 配置
└── static/
    ├── lib/                         # 前端依赖（Vue3, Element Plus, Axios）
    ├── module/                      # 各页面模块
    │   ├── auth.js                  # 认证工具（token 管理、自动登录）
    │   ├── login-modal.js           # 全局登录弹窗 + 导航菜单 + 用户徽章
    │   ├── upload.js                # 统一文件上传（自动分片 >50MB）
    │   ├── login/                   # 登录页
    │   ├── register/                # 注册页
    │   ├── index/                   # 入口页
    │   ├── HomePage/                # 个人网站首页
    │   ├── admin/                   # 后台管理页
    │   ├── squad/                   # 战队管理页
    │   ├── home/                    # 首页内容管理页
    │   ├── router/private/cartoon/  # 漫画管理页
    │   └── test/                    # 工具测试页（RustFS + Valkey）
    └── error/
        └── error.html               # 通用错误页（403/404/500 等）
```

---

## API 路由规范

### 页面 API（被 HTML 页面调用）

路径格式：`/page/{页面名}/api/{功能}`

| 页面 | API 前缀 | Controller |
|------|----------|------------|
| 登录/注册 | `/page/login/api/` | `AuthController` |
| 用户信息 | `/page/login/api/user/` | `UserProfileController` |
| 后台管理 | `/page/admin/api/` | `AdminController` |
| 战队管理 | `/page/squad/api/admin/` | `SquadController` |
| 漫画管理 | `/page/cartoon/api/` | `CartoonController` |
| RustFS 文件 | `/page/rustfs/api/` | `RustFsController` |
| Valkey 键值 | `/page/valkey/api/` | `ValkeyController` |
| 首页数据 | `/home/api/` | `HomePageController` |
| 首页管理 | `/home/admin/api/` | `HomeAdminController` |

### 统一上传 API

| 路径 | 说明 |
|------|------|
| `POST /api/upload/single` | 单次上传（≤50MB） |
| `POST /api/upload/chunk/init` | 分片上传初始化 |
| `POST /api/upload/chunk/upload` | 上传单个分片 |
| `POST /api/upload/chunk/complete` | 合并分片完成上传 |

### 文件代理 API

| 路径 | 说明 |
|------|------|
| `GET /api/public/file?key=&id=` | 公开文件（免认证，封面/图片等） |
| `GET /api/protected/file?key=&id=` | 受保护文件（需认证） |

### 公开 API（免登录）

在 `SecurityConfig.java` 的 `permitAll()` 中配置。

---

## 页面路由

| 路径 | 页面 | 说明 |
|------|------|------|
| `/` `/home` | homePage.html | 个人网站首页 |
| `/login` | login.html | 登录页 |
| `/register` | register.html | 注册页 |
| `/admin` | admin.html | 后台管理 |
| `/squad` | squad.html | 战队管理 |
| `/cartoon` | cartoon/main.html | 漫画管理 |
| `/test` | test/main.html | 工具测试 |
| `/home/admin` | home/admin.html | 首页内容管理 |

---

## 数据库表

| 表名 | 用途 |
|------|------|
| `web_user` | 用户表 |
| `web_user_role` | 用户-角色关联 |
| `sys_role` | 角色表 |
| `sys_menu` | 菜单/页面表（支持树形结构） |
| `sys_role_menu` | 角色-菜单关联 |
| `manga_source` | 漫画/资源数据 |
| `squad` | 战队表 |
| `squad_member` | 战队成员表 |
| `home_content` | 首页内容（JSONB data 字段） |
| `home_module_config` | 首页模块显示配置 |
| `rustfs_file` | RustFS 文件元信息 |

---

## 安全与认证

### JWT 认证流程

1. 用户登录 → 后端返回 JWT token
2. 前端同时存入 `localStorage`（供 AJAX 请求）和 `AUTH_TOKEN` cookie（供页面跳转）
3. `JwtFilter` 从 `Authorization: Bearer` 头或 `AUTH_TOKEN` cookie 提取 token
4. 验证签名后注入 Spring Security 上下文

### 角色体系

- **超级管理员**：角色编码 `SSLingFengDev`（硬编码），拥有全部权限
- **普通角色**：通过 `sys_role` 表管理，支持多角色（`web_user_role` 关联表）
- **默认角色**：注册时自动赋予 `role_code = 'DEFAULT'` 的角色

### 前端认证模块

- `auth.js`：token 管理、自动登录、凭据加密存储
- `login-modal.js`：全局登录弹窗、用户徽章、导航菜单
- `upload.js`：统一文件上传（带 token 认证）

---

## 配置管理

### SystemConfig

- 配置文件：`./config/SystemConfig.json`（相对于 JAR 所在目录）
- 启动时自动加载，文件不存在则创建默认配置
- 运行时可通过 `systemConfig.reload()` 热更新

### RustFS 配置（SystemConfig.json）

```json
{
  "sys.rustfsEndpoint":     { "value": "http://192.168.x.x:9000" },
  "sys.rustfsAccessKey":    { "value": "your-access-key" },
  "sys.rustfsSecretKey":    { "value": "your-secret-key" },
  "sys.rustfsBucket":       { "value": "default" },
  "sys.rustfsPublicUrl":    { "value": "http://cdn.example.com/bucket" },
  "sys.rustfsPathStyleAccess": { "value": true }
}
```

### application.yml 关键配置

```yaml
server:
  port: 8085
spring:
  servlet:
    multipart:
      max-file-size: 200MB
      max-request-size: 200MB
  datasource:
    url: jdbc:postgresql://host:port/dbname
  data:
    redis:
      host: valkey-host
      port: 6379
```

---

## 漫画模块 — Valkey 缓存机制

漫画图片通过 Valkey 缓存路径，避免每次请求查数据库：

| 键格式 | 值 | TTL | 用途 |
|--------|-----|-----|------|
| `{漫画UUID}cover` | 封面绝对路径 | 1h | 列表封面 |
| `{漫画UUID}img{文件名无后缀}` | 图片绝对路径 | 1h | 章节图片 |

图片请求流程：前端获取 key 数组 → 拼接 `/page/cartoon/api/pageImage?key=xxx` → 后端从 Valkey 读路径 → 返回文件

---

## SqlUtil 占位符语法

自研 JDBC 工具，使用自定义占位符语法：

```java
// 查询
SqlUtil.query("SELECT * FROM users WHERE name = {?varchar|name?}", Map.of("name", "张三"));

// 单行查询
SqlUtil.row("SELECT * FROM users WHERE id = {?integer|id?}", Map.of("id", 1));

// 执行
SqlUtil.exec("UPDATE users SET name = {?varchar|n?} WHERE id = {?integer|id?}", Map.of("n", "李四", "id", 1));

// JSONB 字段：用 varchar 传值 + SQL 层面 ::jsonb 转型
SqlUtil.exec("INSERT INTO t (data) VALUES ({?varchar|d?}::jsonb)", Map.of("d", jsonString));
```

占位符格式：`{?类型|参数名?}`，类型对应 `java.sql.Types` 常量名（varchar, integer, boolean, timestamp 等）。

**注意**：`getSqlType()` 不支持 `jsonb` 类型，JSONB 字段需用 `varchar` 传值 + `::jsonb` SQL 转型。

---

## 目录结构映射（漫画资源）

```
{mediaRootPath}/{type映射}/{path}/{directory}/cover.jpg       → 封面
{mediaRootPath}/{type映射}/{path}/{directory}/{章节index}/1.jpg  → 章节图片
```

- `type=漫画` → 目录名 `cartoon`
- `type=coser` → 目录名 `coser`
- 章节信息存储在 `manga_source.chapters` 字段（JSON 数组）

### 历史遗留路径适配

Windows 目录名末尾的 `.` 会被强制替换为 `_`，需根据 `path` 日期判断修正方式：
- `path >= "20251010"` → 去掉末尾点号
- `path < "20251010"` → 末尾点号替换为下划线

---

## 开发规范

### 新增页面步骤

1. **后端 Controller**：放在 `page/{页面名}/` 下
2. **页面路由**：在 `LoginRouter.java` 添加 `@GetMapping`
3. **前端 HTML**：放在 `static/module/{页面名}/`
4. **安全配置**：需要公开的接口在 `SecurityConfig.java` 的 `permitAll()` 中添加
5. **引入公共模块**：
   ```html
   <script src="/public/res/module/auth.js"></script>
   <script src="/public/res/module/login-modal.js"></script>
   <script src="/public/res/lib/vue.global.prod.js"></script>
   <script src="/public/res/lib/element-plus@2.14.4_index.full.js"></script>
   <script>LoginModal.init();</script>
   ```

### 前端 Vue 3 + Element Plus 规范

- 使用 `Vue.createApp({...}).use(ElementPlus).mount('#app')` 创建实例
- 消息提示：`ElementPlus.ElMessage.success('...')`
- 确认框：`ElementPlus.ElMessageBox.confirm('...')`
- 插槽语法：`#default="{ row }"`, `#footer`, `#append`
- 弹窗绑定：`v-model="dialogVisible"`（不用 `:visible.sync`）
- 事件修饰符：`@submit.prevent`（不用 `.native`）
- 尺寸：`size="small"`（不用 `size="mini"`）

### Java 注意事项

- Spring Boot 4 使用 **Jakarta EE**，包名是 `jakarta.servlet`，不是 `javax.servlet`
- `Map.of()` 最多支持 10 对键值（20 个参数），超过时使用 `LinkedHashMap`
- SQL 工具使用自定义占位符 `{?类型|参数名?}`，不是 `?` 占位符
- JSONB 字段操作：用 `varchar` 传值 + `::jsonb` SQL 转型
- JSON 解析使用 Hutool 的 `JSONObject` / `JSONArray`
