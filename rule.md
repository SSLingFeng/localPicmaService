# localPicmaService — 项目规则文档

## 项目简介

localPicmaService 是一个基于 **Spring Boot 4 + Java 21 + PostgreSQL** 的本地多功能服务端，主要用途：

- **漫画资源管理**：漫画/Coser 资源的浏览、搜索、章节阅读、收藏/厌恶、推荐
- **个人网站首页**：游戏日志、摄影展示、生活碎片、工作轨迹四大模块
- **后台管理**：用户、角色、菜单、战队管理
- **文件管理**：RustFS 对象存储 + Valkey 缓存
- **工具模块**：SQLite 数据导入、章节压缩、漫画去重

---

## 技术栈

| 维度 | 技术 |
|------|------|
| 框架 | Spring Boot 4.1.0 (Jakarta EE) |
| Java | 21 |
| 数据库 | PostgreSQL (HikariCP 连接池) |
| 缓存 | Valkey / Redis (spring-data-redis) + Caffeine (JVM 本地缓存) |
| 对象存储 | RustFS (S3 协议兼容, AWS SDK v2) |
| 安全 | Spring Security + JWT (Hutool JWT) |
| 密码加密 | BCrypt |
| 工具库 | Hutool 5.8.32, Lombok |
| 图片处理 | WebP ImageIO (webp-imageio 0.1.6) |
| SQLite | SQLite JDBC 3.45.3.0（用于导入 .db 文件） |
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
│   ├── home/                        # 首页数据 + 首页内容管理 + WebP图片接口
│   ├── cartoon/controller/          # 漫画模块（用户端 + 管理端）
│   │   ├── CartoonController.java   # 用户端：列表/推荐/收藏/详情/章节图片
│   │   └── MangaAdminController.java # 管理端：增删改查/去重
│   ├── comic/controller/            # SQLite 数据导入
│   │   └── SqliteImportController.java
│   ├── rustfs/controller/           # RustFS 文件管理
│   ├── valkey/controller/           # Valkey 键值管理
│   ├── upload/                      # 统一文件上传（支持分片）
│   ├── proxy/                       # 文件代理（公开/受保护）
│   ├── login/                       # 页面路由（LoginRouter.java）
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
    ├── Valkey/                      # Valkey 键值工具
    │   └── ValkeyUtil.java          # get/set/del/bytes 等操作
    └── MangaZipController.java      # 漫画章节压缩工具

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
    │   ├── HomePage/                # 个人网站首页（游戏/摄影/生活/工作）
    │   ├── admin/                   # 后台管理页
    │   │   ├── admin.html           # 用户管理
    │   │   └── manga/               # 漫画后台管理（含导入/压缩/去重）
    │   │       ├── main.html
    │   │       └── app.js
    │   ├── squad/                   # 战队管理页
    │   ├── home/                    # 首页内容管理页
    │   ├── router/private/cartoon/  # 漫画用户页（推荐/搜索/我的）
    │   │   ├── main.html
    │   │   ├── app.js
    │   │   └── style.css
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
| 漫画用户端 | `/page/cartoon/api/` | `CartoonController` |
| 漫画管理端 | `/page/cartoon/admin/api/` | `MangaAdminController` |
| SQLite 导入 | `/page/comic/import/api/` | `SqliteImportController` |
| RustFS 文件 | `/page/rustfs/api/` | `RustFsController` |
| Valkey 键值 | `/page/valkey/api/` | `ValkeyController` |
| 首页数据 | `/home/api/` | `HomePageController` |
| 首页管理 | `/home/admin/api/` | `HomeAdminController` |

### 漫画用户端 API（CartoonController）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/page/cartoon/api/list` | 搜索漫画列表（支持偏好过滤） |
| `GET` | `/page/cartoon/api/recommend` | 随机推荐 20 部（排除收藏和厌恶） |
| `GET` | `/page/cartoon/api/favorites` | 当前用户收藏列表 |
| `GET` | `/page/cartoon/api/detail?id=` | 漫画详情（含章节列表） |
| `POST` | `/page/cartoon/api/toggleFavorite` | 切换收藏（基于 picg_id） |
| `POST` | `/page/cartoon/api/toggleDislike` | 切换厌恶（基于 picg_id） |
| `POST` | `/page/cartoon/api/searchTags` | 标签搜索 |
| `POST` | `/page/cartoon/api/searchCategories` | 类别搜索 |
| `POST` | `/page/cartoon/api/chapterImages` | 获取章节图片列表 |
| `GET` | `/page/cartoon/api/cover?key=` | 封面图片 |
| `GET` | `/page/cartoon/api/pageImage?key=` | 章节图片（Caffeine 缓存 + zip 解压） |

### 漫画管理端 API（MangaAdminController）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/page/cartoon/admin/api/list` | 漫画列表（含已删除） |
| `POST` | `/page/cartoon/admin/api/softDelete` | 软删除 |
| `POST` | `/page/cartoon/admin/api/restore` | 恢复删除 |
| `POST` | `/page/cartoon/admin/api/upload-image` | 上传图片 |
| `POST` | `/page/cartoon/admin/api/searchTags` | 标签搜索 |
| `POST` | `/page/cartoon/admin/api/searchCategories` | 类别搜索 |
| `GET` | `/page/cartoon/admin/api/dedup/scan` | 扫描重复 picg_id |
| `POST` | `/page/cartoon/admin/api/dedup/execute` | 执行去重 |
| `GET` | `/page/cartoon/admin/api/cover` | 封面图片 |
| `GET` | `/page/cartoon/admin/api/pageImage` | 章节图片 |
| `POST` | `/page/cartoon/admin/api/chapterImages` | 章节图片列表 |

### 工具 API

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/tool/manga-zip/status` | 章节压缩任务状态 |
| `POST` | `/tool/manga-zip/start` | 启动压缩任务 |
| `POST` | `/tool/manga-zip/stop` | 停止压缩任务 |
| `GET` | `/page/comic/import/api/status` | SQLite 导入任务状态 |
| `POST` | `/page/comic/import/api/upload` | 上传 .db 文件并导入 |
| `POST` | `/page/comic/import/api/stop` | 停止导入任务 |

### 统一上传 API

| 路径 | 说明 |
|------|------|
| `POST /api/upload/single` | 单次上传（≤50MB） |
| `POST /api/upload/chunk/init` | 分片上传初始化 |
| `POST /api/upload/chunk/upload` | 上传单个分片 |
| `POST /api/upload/chunk/complete` | 合并分片完成上传 |

### 公开 API（免登录）

在 `SecurityConfig.java` 的 `permitAll()` 中配置：

| 路径 | 说明 |
|------|------|
| `/` `/login` `/register` `/home` | 页面路由 |
| `/home/api/**` | 首页数据接口 |
| `/public/res/**` | 静态资源 |
| `/page/login/api/login` | 登录接口 |
| `/page/login/api/check-token` | Token 验证 |
| `/page/login/api/register` | 注册接口 |
| `/page/cartoon/api/cover` | 漫画封面 |
| `/page/cartoon/api/pageImage` | 章节图片 |
| `/page/rustfs/api/download` | RustFS 下载 |
| `/api/public/file` | 公开文件代理 |
| `/api/public/home-image` | 首页 WebP 图片 |

---

## 页面路由

| 路径 | 页面 | 说明 |
|------|------|------|
| `/` `/home` | HomePage/homePage.html | 个人网站首页 |
| `/login` | login/login.html | 登录页 |
| `/register` | register/register.html | 注册页 |
| `/admin` | admin/admin.html | 后台管理 |
| `/squad` | squad/squad.html | 战队管理 |
| `/cartoon` | router/private/cartoon/main.html | 漫画用户页（推荐/搜索/我的） |
| `/admin/manga` | admin/manga/main.html | 漫画后台管理（管理/导入/压缩/去重） |
| `/test` | test/main.html | 工具测试 |
| `/home/admin` | home/admin.html | 首页内容管理 |

---

## 数据库表

### 建表规范

所有新建表必须包含以下系统内建字段：

```sql
CREATE TABLE public.{table_name}
(
    id           varchar(40)       not null    primary key,
    ver          integer default 0 not null,
    create_date  timestamp,
    update_date  timestamp,
    creator_id   varchar(40),
    creator_name varchar(50),
    updator_id   varchar(40),
    updator_name varchar(50),
    del_flag     integer default 0,
    -- 业务字段 ...
);

COMMENT ON TABLE  public.{table_name}                   IS '表说明';
COMMENT ON COLUMN public.{table_name}.id                IS '系统内建字段，主键';
COMMENT ON COLUMN public.{table_name}.ver               IS '系统内建字段，版本号';
COMMENT ON COLUMN public.{table_name}.create_date       IS '系统内建字段，创建时间';
COMMENT ON COLUMN public.{table_name}.update_date       IS '系统内建字段，最近更新时间';
COMMENT ON COLUMN public.{table_name}.creator_id        IS '系统内建字段，创建账号id';
COMMENT ON COLUMN public.{table_name}.creator_name      IS '系统内建字段，创建账号名称';
COMMENT ON COLUMN public.{table_name}.updator_id        IS '系统内建字段，最近更新账号id';
COMMENT ON COLUMN public.{table_name}.updator_name      IS '系统内建字段，最近更新账号名称';
COMMENT ON COLUMN public.{table_name}.del_flag          IS '系统内建字段，删除标记，0未删除，1删除；默认0';

ALTER TABLE public.{table_name} OWNER TO czw;
```

### 表清单

| 表名 | 用途 | 说明 |
|------|------|------|
| `web_user` | 用户表 | |
| `web_user_role` | 用户-角色关联 | |
| `sys_role` | 角色表 | |
| `sys_menu` | 菜单/页面表 | 支持树形结构 |
| `sys_role_menu` | 角色-菜单关联 | |
| `manga_source` | 漫画/资源数据 | 主表，picg_id 为外部平台 ID |
| `manga_user_preference` | 漫画用户偏好 | 收藏/厌恶，通过 `picg_id` 关联 |
| `squad` | 战队表 | |
| `squad_member` | 战队成员表 | |
| `home_content` | 首页内容 | 新增 title/content/order_num/date_time 列 |
| `home_module_config` | 首页模块显示配置 | |
| `rustfs_file` | RustFS 文件元信息 | |

### home_content 表结构（更新）

```sql
-- 新增列（已存在则跳过）
ALTER TABLE home_content ADD COLUMN IF NOT EXISTS title VARCHAR(50);
ALTER TABLE home_content ADD COLUMN IF NOT EXISTS content TEXT;
ALTER TABLE home_content ADD COLUMN IF NOT EXISTS order_num INTEGER DEFAULT 0;
ALTER TABLE home_content ADD COLUMN IF NOT EXISTS date_time TIME;
```

- `data` 列（JSONB）仅存放图片 ID 数组：`[{"file_id": "xxx", "order_num": 0}]`
- 图片通过 `/api/public/home-image?id={file_id}` 访问（WebP 格式）

### manga_user_preference 表结构

```sql
CREATE TABLE public.manga_user_preference
(
    id           varchar(40)       not null primary key,
    ver          integer default 0 not null,
    create_date  timestamp,
    update_date  timestamp,
    creator_id   varchar(40),
    creator_name varchar(50),
    updator_id   varchar(40),
    updator_name varchar(50),
    del_flag     integer default 0,
    user_id      varchar(40)       not null,   -- 关联 web_user.id
    picg_id      varchar(40)       not null,   -- 关联 manga_source.picg_id
    pref_type    smallint          not null    -- 1=收藏, -1=厌恶
);
```

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

- `auth.js`：token 管理、自动登录、凭据加密存储、`checkToken()` 不在网络错误时清除 cookie
- `login-modal.js`：全局登录弹窗、用户徽章、导航菜单
- `upload.js`：统一文件上传（带 token 认证）

---

## 漫画模块架构

### 三 Tab 架构（用户端 `/cartoon`）

| Tab | 说明 | 数据来源 |
|-----|------|---------|
| 推荐 | 随机推荐 20 部 | `GET /page/cartoon/api/recommend` |
| 搜索 | 搜索+分页 | `POST /page/cartoon/api/list` |
| 我的 | 收藏列表 | `GET /page/cartoon/api/favorites` |

- 移动端底部导航栏（`z-index: 1000`）
- 漫画详情弹窗（`z-index: 2000`）覆盖导航
- 全屏阅读器（`z-index: 3000`）覆盖所有
- 所有 Tab 点击漫画卡片统一调用 `openChapters()` → `GET /page/cartoon/api/detail?id=xxx`

### 四 Tab 架构（管理端 `/admin/manga`）

| Tab | 说明 |
|-----|------|
| 漫画管理 | 搜索、删除/恢复、详情查看 |
| SQLite 导入 | 上传 .db 文件导入数据 |
| 章节压缩 | 批量压缩章节为 zip |
| 漫画去重 | 按 picg_id 去重 |

### 收藏/厌恶系统

- 通过 `manga_user_preference` 表存储，关联 `picg_id`（非 manga_source.id）
- `pref_type = 1` 收藏，`pref_type = -1` 厌恶
- 收藏和厌恶互斥：收藏时自动取消厌恶，反之亦然
- 软删除（`del_flag`），可恢复

### 图片缓存机制

**Caffeine 本地缓存**（章节图片）：
- 最大 4000 张
- 动态 TTL：默认 1h，>300张或>300MB 的章节 30min，>600张或>600MB 的章节 15min
- Valkey 存储元数据：`{key}:meta` = `{zipPath}|{entryName}|{imageCount}|{zipSizeBytes}`

**Valkey 缓存**（封面路径）：
- 键：`{漫画ID}cover` → 值：封面绝对路径，TTL 1h

**图片请求流程**：
1. 前端获取 key 数组
2. 请求 `/page/cartoon/api/pageImage?key=xxx`
3. 先查 Caffeine 缓存 → 命中直接返回
4. 未命中 → 读取 Valkey 元数据 → 从 zip 解压或读文件 → 写入 Caffeine → 返回

### 首页图片（WebP）

- 接口：`GET /api/public/home-image?id={rustfs_file表ID}`
- 免登录
- 从 RustFS 下载 → 转 WebP（webp-imageio）→ 返回
- 缓存 1 小时

---

## 首页内容管理

### 数据结构变更

`home_content` 表新增独立字段：`title`, `content`, `order_num`, `date_time`（TIME 类型）

`data` 列（JSONB）仅存放图片数组：
```json
[{"file_id": "xxx", "order_num": 0}, {"file_id": "yyy", "order_num": 1}]
```

### 图片上传流程

1. 上传图片 → RustFS 存储 → 返回 `rustfs_file` 表 ID
2. 前端保存 ID 到 `data` 列的图片数组
3. 前端显示时通过 `/api/public/home-image?id={file_id}` 获取 WebP 图片

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

// Timestamp 字段：用 varchar 传值 + SQL 层面 ::timestamp 转型
SqlUtil.exec("INSERT INTO t (time) VALUES ({?varchar|t?}::timestamp)", Map.of("t", timeStr));
```

占位符格式：`{?类型|参数名?}`，类型对应 `java.sql.Types` 常量名。

**类型转换规则**：

| SQL 类型 | 占位符类型 | 备注 |
|----------|-----------|------|
| varchar/text | `{?varchar\|...?}` | |
| integer | `{?integer\|...?}` | |
| bigint | `{?bigint\|...?}` | |
| boolean | `{?boolean\|...?}` | |
| timestamp | `{?varchar\|...?}::timestamp` | varchar 传入 + SQL 转型 |
| jsonb | `{?varchar\|...?}::jsonb` | varchar 传入 + SQL 转型 |
| time | `{?time\|...?}` | |

---

## 目录结构映射（漫画资源）

```
{mediaRootPath}/{type映射}/{path}/{directory}/cover.jpg       → 封面
{mediaRootPath}/{type映射}/{path}/{directory}/{章节index}/1.jpg  → 章节图片
{mediaRootPath}/{type映射}/{path}/{directory}/{章节index}.zip   → 压缩包
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

### 移动端适配规范

- 使用 `isMobile` 响应式状态（`window.innerWidth <= 700`）
- 监听 `resize` 事件更新状态
- 底部导航栏使用 `position: fixed; bottom: 0`，`z-index: 1000`
- 弹窗使用 `:fullscreen="isMobile"` 实现响应式
- 弹窗 `z-index` 需高于底部导航（建议 2000+）
- 阅读器 `z-index` 最高（建议 3000）
- 弹窗/阅读器打开时隐藏浮动元素（用户徽章、导航菜单）

### Java 注意事项

- Spring Boot 4 使用 **Jakarta EE**，包名是 `jakarta.servlet`，不是 `javax.servlet`
- SQL 工具使用自定义占位符 `{?类型|参数名?}`，不是 `?` 占位符
- JSONB 字段操作：用 `varchar` 传值 + `::jsonb` SQL 转型
- Timestamp 字段操作：用 `varchar` 传值 + `::timestamp` SQL 转型
- JSON 解析使用 Hutool 的 `JSONObject` / `JSONArray`
- SqlUtil 返回的 boolean 字段是 `int`（0/1），需要手动转换
- SqlUtil 返回的 time 字段是 `java.util.Date`，需要格式化为字符串

### Map.of() 参数限制问题

**问题**：
编译时报错 `actual argument list and formal argument list differ in length`，提示找不到合适的 `Map.of()` 方法。

**原因**：
Java 的 `Map.of()` 工厂方法最多只支持 **10 对键值（20 个参数）**。当构建的 Map 超过 10 个键值对时，没有对应的重载方法，编译失败。

```java
// 错误示例：11 对键值，编译失败
Map.of("k1", v1, "k2", v2, ..., "k11", v11);  // ❌
```

**解决方法**：
超过 10 对键值时，使用 `LinkedHashMap` 替代：

```java
// 正确做法
Map<String, Object> params = new LinkedHashMap<>();
params.put("k1", v1);
params.put("k2", v2);
// ... 任意数量
params.put("k11", v11);
SqlUtil.exec(sql, params);
```

**适用场景**：
- `SqlUtil.exec()` / `SqlUtil.query()` / `SqlUtil.row()` 的参数 Map
- `Map.of()` 返回值（如接口返回 JSON）

**经验总结**：
凡是需要动态构建参数、字段较多的场景，统一使用 `LinkedHashMap`，避免后续添加字段时踩坑。
- SqlUtil 返回的 time 字段是 `java.util.Date`，需要格式化为字符串
