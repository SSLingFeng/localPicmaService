# localPicmaService — 项目知识库

> 本文档是项目的完整知识库，包含架构、组件、API、数据库、开发规范等所有关键信息。
> 最后更新：2026-09-09

---

## 1. 项目概览

**localPicmaService** 是一个基于 Spring Boot 4 + Java 21 + PostgreSQL 的本地多功能服务端。

### 核心功能模块

| 模块 | 说明 | 主要页面 |
|------|------|---------|
| 漫画资源 | 浏览、搜索、章节阅读、收藏/厌恶、推荐 | `/cartoon` |
| 漫画后台 | 管理、SQLite导入、章节压缩、去重 | `/admin/manga` |
| 个人网站 | 游戏日志、摄影展示、生活碎片、工作轨迹 | `/home` |
| 首页管理 | 内容管理、多图上传 | `/home/admin` |
| 用户系统 | 登录、注册、角色权限 | `/login`, `/admin` |
| FRP 控制 | 客户端实例管理、代理规则、配置生成 | `/frp` |
| 工具 | RustFS文件管理、Valkey键值、压缩工具 | `/test`, `/tool/*` |

### 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 4.1.0 (Jakarta EE) |
| 语言 | Java 21 |
| 数据库 | PostgreSQL + HikariCP |
| 缓存 | Valkey/Redis + Caffeine (JVM本地) |
| 对象存储 | RustFS (S3协议兼容) |
| 安全 | Spring Security + JWT (Hutool) |
| 前端 | Vue 3 + Element Plus 2.14.4 (无构建工具) |
| 图片处理 | WebP ImageIO |

---

## 2. 项目结构

### 后端 (`src/main/java/com/example/localPicmaService/`)

```
├── config/
│   ├── SystemConfig.java          # 系统配置（JSON文件加载，支持热更新）
│   ├── PasswordConfig.java        # BCrypt密码编码器
│   └── ErrorPageConfig.java       # 自定义错误页
├── security/
│   ├── SecurityConfig.java        # 安全过滤链（permitAll白名单）
│   ├── JwtFilter.java             # JWT过滤器（Header + Cookie）
│   └── CustomUserDetailsService.java
├── page/                          # 页面API
│   ├── auth/controller/           # 登录、注册、用户信息
│   ├── admin/controller/          # 后台管理
│   ├── squad/controller/          # 战队管理
│   ├── home/                      # 首页数据+管理+WebP图片
│   ├── cartoon/controller/        # 漫画用户端+管理端
│   ├── comic/controller/          # SQLite导入
│   ├── frp/controller/            # FRP控制端
│   ├── rustfs/controller/         # RustFS管理
│   ├── valkey/controller/         # Valkey管理
│   ├── upload/                    # 统一上传（分片）
│   ├── proxy/                     # 文件代理
│   ├── login/LoginRouter.java     # 所有页面路由
│   └── ResourceController.java    # 静态资源
├── api/                           # 纯API
│   ├── comic/                     # PicACG客户端
│   └── command/                   # 进程管理
├── common/
│   ├── ZMessage.java              # 统一响应体
│   ├── DramVariable.java          # 线程局部变量
│   └── DataSourceControl.java
└── tool/
    ├── SQLTool/SqlUtil.java       # 自研SQL工具
    ├── RustFs/RustFsUtil.java     # S3文件管理
    ├── Valkey/ValkeyUtil.java     # 键值操作
    └── MangaZipController.java    # 章节压缩
```

### 前端 (`src/main/resources/static/`)

```
├── lib/                           # Vue3, Element Plus, Axios（本地）
├── module/
│   ├── auth.js                    # Token管理、自动登录
│   ├── login-modal.js             # 全局登录弹窗+导航菜单
│   ├── upload.js                  # 统一上传（>50MB自动分片）
│   ├── HomePage/                  # 个人网站首页
│   ├── router/private/cartoon/    # 漫画用户页（推荐/搜索/我的）
│   ├── admin/manga/               # 漫画后台（管理/导入/压缩/去重）
│   ├── frp/                       # FRP控制端
│   ├── home/                      # 首页内容管理
│   ├── admin/                     # 用户管理
│   ├── squad/                     # 战队管理
│   └── test/                      # 工具测试
└── error/error.html               # 通用错误页
```

---

## 3. 核心组件详解

### 3.1 SystemConfig

**文件**: `config/SystemConfig.java`
**配置文件**: `./config/SystemConfig.json`（相对于JAR目录）

| 字段 | 说明 |
|------|------|
| `systemName` | 系统名称 |
| `logPath` | 日志路径 |
| `mediaRootPath` | 媒体资源根目录（漫画/coser） |
| `frpBasePath` | FRP配置文件主路径 |
| `rustfs*` | RustFS/S3配置 |

**特性**:
- 启动时自动加载，不存在则创建默认配置
- 运行时可通过 `systemConfig.reload()` 热更新

### 3.2 SqlUtil（自研SQL工具）

**占位符语法**: `{?类型|参数名?}`

| 类型 | 示例 | 说明 |
|------|------|------|
| `varchar` | `{?varchar\|name?}` | 字符串 |
| `integer` | `{?integer\|id?}` | 整数 |
| `bigint` | `{?bigint\|size?}` | 长整数 |
| `boolean` | `{?boolean\|flag?}` | 布尔 |
| `time` | `{?time\|t?}` | 时间 |
| `varchar + ::jsonb` | `{?varchar\|data?}::jsonb` | JSONB字段 |
| `varchar + ::timestamp` | `{?varchar\|ts?}::timestamp` | 时间戳 |

**常用方法**:
```java
SqlUtil.query(sql, params, maxRows)  // 多行查询
SqlUtil.row(sql, params)             // 单行查询
SqlUtil.exec(sql, params)            // 执行更新
SqlUtil.sync("table").insert(list).commit()  // 批量插入
```

**注意事项**:
- boolean字段返回 `int`（0/1），需手动转换
- time字段返回 `java.util.Date`，需格式化

### 3.3 ValkeyUtil

**功能**: get/set/del + bytes存取

| 方法 | 说明 |
|------|------|
| `set(key, value, seconds)` | 设置键值（带过期） |
| `setEx(key, value, seconds)` | 设置键值（秒） |
| `get(key)` | 获取值 |
| `setBytesEx(key, data, seconds)` | 存储字节（Base64） |
| `getBytes(key)` | 读取字节 |
| `del(key)` | 删除 |
| `exists(key)` | 判断存在 |

### 3.4 RustFsUtil

**功能**: S3协议文件管理

| 方法 | 说明 |
|------|------|
| `upload(stream, path, name, type)` | 上传文件，返回DB ID |
| `download(dbId)` | 下载文件流 |
| `delete(dbId)` | 删除文件 |
| `getFileInfo(dbId)` | 获取文件元信息 |

### 3.5 JwtFilter

**认证流程**:
1. 从 `Authorization: Bearer` 头提取 token
2. 若无，从 `AUTH_TOKEN` Cookie 提取
3. 验证签名后注入 Spring Security 上下文
4. 超级管理员（`SSLingFengDev`）自动获得 `ROLE_SUPER_ADMIN`

---

## 4. 数据库

### 建表规范

所有新建表必须包含以下系统字段：

```sql
CREATE TABLE public.{table_name}
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
    -- 业务字段...
);
```

### 表清单

| 表名 | 说明 |
|------|------|
| `web_user` | 用户表 |
| `web_user_role` | 用户-角色关联 |
| `sys_role` | 角色表 |
| `sys_menu` | 菜单表（树形） |
| `sys_role_menu` | 角色-菜单关联 |
| `manga_source` | 漫画资源（picg_id为外部平台ID） |
| `manga_user_preference` | 收藏/厌恶（关联picg_id） |
| `home_content` | 首页内容（data列存图片ID数组） |
| `home_module_config` | 首页模块配置 |
| `rustfs_file` | 文件元信息 |
| `frp_client` | FRP客户端实例 |
| `frp_proxy` | FRP代理规则 |

---

## 5. API 路由

### 公开API（免登录）

| 路径 | 说明 |
|------|------|
| `/`, `/login`, `/register`, `/home` | 页面路由 |
| `/home/api/**` | 首页数据 |
| `/public/res/**` | 静态资源 |
| `/page/login/api/login` | 登录 |
| `/page/login/api/check-token` | Token验证 |
| `/page/cartoon/api/cover` | 漫画封面 |
| `/page/cartoon/api/pageImage` | 章节图片 |
| `/api/public/file` | 公开文件 |
| `/api/public/home-image` | 首页WebP图片 |

### 漫画用户端 (`/page/cartoon/api/`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/list` | 搜索列表（支持偏好过滤） |
| GET | `/recommend` | 随机推荐20部 |
| GET | `/favorites` | 收藏列表 |
| GET | `/detail?id=` | 漫画详情 |
| POST | `/toggleFavorite` | 切换收藏（picg_id） |
| POST | `/toggleDislike` | 切换厌恶（picg_id） |
| POST | `/chapterImages` | 章节图片列表 |
| GET | `/pageImage?key=` | 章节图片（Caffeine缓存） |

### 漫画管理端 (`/page/cartoon/admin/api/`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/list` | 列表（含已删除） |
| POST | `/softDelete` | 软删除 |
| POST | `/restore` | 恢复 |
| GET | `/dedup/scan` | 扫描重复 |
| POST | `/dedup/execute` | 执行去重 |

### FRP控制 (`/page/frp/api/`)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/client/list` | 客户端列表 |
| POST | `/client/add` | 新增客户端 |
| POST | `/client/update` | 更新客户端 |
| POST | `/client/delete` | 删除客户端 |
| POST | `/client/generate-config` | 生成配置文件 |
| POST | `/client/reload` | 重载客户端 |
| GET | `/proxy/list` | 代理列表 |
| POST | `/proxy/add` | 新增代理 |
| POST | `/proxy/update` | 更新代理 |
| POST | `/proxy/delete` | 删除代理 |
| POST | `/proxy/toggle` | 切换启用/禁用 |

---

## 6. 漫画模块架构

### 用户端（三Tab）

| Tab | 数据源 | 说明 |
|-----|--------|------|
| 推荐 | `GET /recommend` | 随机20部，排除收藏+厌恶 |
| 搜索 | `POST /list` | 分页搜索，支持偏好过滤 |
| 我的 | `GET /favorites` | 收藏列表 |

**图片缓存**:
- Caffeine本地缓存（4000张，动态TTL）
- Valkey元数据（`{key}:meta`）
- 封面：Valkey路径缓存（1h）

### 管理端（四Tab）

| Tab | 功能 |
|-----|------|
| 漫画管理 | CRUD、删除/恢复、详情查看 |
| SQLite导入 | 上传.db文件导入数据 |
| 章节压缩 | 批量压缩为zip |
| 漫画去重 | 按picg_id去重 |

### 收藏/厌恶系统

- 关联字段：`picg_id`（非manga_source.id）
- `pref_type`: 1=收藏, -1=厌恶
- 互斥：收藏自动取消厌恶，反之亦然
- 软删除（del_flag），可恢复

---

## 7. FRP控制端

### 表结构

**frp_client**（客户端实例）:
- `server_addr`, `server_port` - 服务端连接
- `auth_token` - 认证Token
- `web_server_*` - WebServer管理配置
- `config_path` - 配置文件相对路径

**frp_proxy**（代理规则）:
- `client_id` - 关联客户端
- `proxy_name`, `proxy_type` - 代理配置
- `local_ip`, `local_port` - 本地地址
- `remote_port` - 远程端口
- `secret_key` - STCP/XTCP密钥

### 配置生成

从数据库读取 → 组装JSON → 写入文件系统（基于`frpBasePath` + `config_path`）

---

## 8. 开发规范

### 新增页面步骤

1. 后端Controller放在 `page/{页面名}/`
2. LoginRouter添加 `@GetMapping`
3. 前端HTML放在 `static/module/{页面名}/`
4. SecurityConfig的 `permitAll()` 添加公开接口
5. 引入公共模块：`auth.js`, `login-modal.js`, Vue3, Element Plus

### 前端规范

```javascript
// Vue3实例创建
Vue.createApp({...}).use(ElementPlus).mount('#app');

// 消息提示
ElementPlus.ElMessage.success('...');

// 确认框
ElementPlus.ElMessageBox.confirm('...');

// 弹窗绑定
v-model="dialogVisible"

// 尺寸
size="small"
```

### 移动端适配

- `isMobile` 响应式状态（`window.innerWidth <= 700`）
- 底部导航栏：`position: fixed; bottom: 0; z-index: 1000`
- 弹窗：`:fullscreen="isMobile"`, `z-index: 2000`
- 阅读器：`z-index: 3000`
- 弹窗打开时隐藏浮动元素

---

## 9. 常见问题与解决方案

### 9.1 Map.of() 参数限制

**问题**: 编译报错 `actual argument list and formal argument list differ in length`

**原因**: `Map.of()` 最多支持10对键值（20个参数）

**解决**: 超过10对时使用 `LinkedHashMap`：
```java
Map<String, Object> params = new LinkedHashMap<>();
params.put("k1", v1);
params.put("k2", v2);
// ... 任意数量
SqlUtil.exec(sql, params);
```

### 9.2 SqlUtil 返回类型问题

**boolean字段**: 返回 `int`（0/1），不是 `Boolean`
```java
Object enabled = row.get("enabled");
if (enabled instanceof Number) {
    boolean val = ((Number) enabled).intValue() != 0;
}
```

**time字段**: 返回 `java.util.Date`，需格式化
```java
Object dt = row.get("date_time");
if (dt instanceof java.util.Date) {
    String formatted = new SimpleDateFormat("HH:mm").format((Date) dt);
}
```

### 9.3 JSONB 字段操作

```java
// 写入：varchar + ::jsonb
SqlUtil.exec("INSERT INTO t (data) VALUES ({?varchar|d?}::jsonb)",
    Map.of("d", jsonString));

// 读取：data::text as data
SqlUtil.query("SELECT data::text as data FROM t WHERE id = ?", params);
```

### 9.4 Element Plus 2.x 样式问题

**问题**: Vue2→Vue3升级后，input/select背景变白

**原因**: Element Plus 2.x 新增 `.el-input__wrapper` 包裹层

**解决**: 覆盖wrapper样式：
```css
.el-input__wrapper {
    background-color: var(--surface)!important;
    box-shadow: 0 0 0 1px var(--border) inset!important;
}
```

### 9.5 el-skeleton 插槽问题

**问题**: `<template>` 无名插槽不渲染

**解决**: 使用显式命名插槽：
```html
<el-skeleton :loading="loading">
    <template #template>...</template>
    <template #default>...</template>
</el-skeleton>
```

### 9.6 JWT Token 丢失问题

**问题**: 页面跳转时403，token未带上

**原因**: `checkToken()` 网络错误时清除了cookie

**解决**: 只在服务端明确返回401时清除认证，网络错误不清除cookie

---

## 10. 配置文件说明

### SystemConfig.json

```json
{
  "sys.systemName": { "value": "localPicmaService" },
  "sys.mediaRootPath": { "value": "/path/to/media" },
  "sys.frpBasePath": { "value": "/path/to/frp" },
  "sys.rustfsEndpoint": { "value": "http://192.168.x.x:9000" },
  "sys.rustfsAccessKey": { "value": "xxx" },
  "sys.rustfsSecretKey": { "value": "xxx" },
  "sys.rustfsBucket": { "value": "default" },
  "sys.rustfsPublicUrl": { "value": "http://cdn.example.com" }
}
```

### application.yml

```yaml
server:
  port: 8085
spring:
  datasource:
    url: jdbc:postgresql://host:port/dbname
    username: czw
  data:
    redis:
      host: 192.168.31.6
      port: 6379
```

---

## 11. 漫画资源目录结构

```
{mediaRootPath}/{type}/{path}/{directory}/
├── cover.jpg                    # 封面
├── 1/                           # 章节1
│   ├── 1.jpg
│   ├── 2.jpg
│   └── ...
├── 1.zip                        # 章节1压缩包
├── 2/                           # 章节2
└── 2.zip
```

- `type`: `cartoon`（漫画）或 `coser`
- `path`: 日期目录（如 `20250801`）
- `directory`: 漫画目录名

**历史遗留路径适配**:
- `path >= "20251010"` → 去掉末尾点号
- `path < "20251010"` → 末尾点号替换为下划线

---

## 12. 页面路由汇总

| 路径 | 页面 | 说明 |
|------|------|------|
| `/` `/home` | HomePage/ | 个人网站 |
| `/login` | login/ | 登录 |
| `/register` | register/ | 注册 |
| `/admin` | admin/ | 用户管理 |
| `/squad` | squad/ | 战队管理 |
| `/cartoon` | router/private/cartoon/ | 漫画用户端 |
| `/admin/manga` | admin/manga/ | 漫画后台 |
| `/frp` | frp/ | FRP控制端 |
| `/home/admin` | home/ | 首页管理 |
| `/test` | test/ | 工具测试 |
| `/tool/manga-zip` | tool/ | 章节压缩 |
| `/tool/sqlite-import` | tool/ | SQLite导入 |
