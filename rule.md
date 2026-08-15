# localPicmaService — 项目规则文档

## 项目简介

localPicmaService 是一个基于 **Spring Boot 4.0.1 + Java 21 + PostgreSQL** 的本地多功能服务端，主要用途是漫画源管理、个人网站管理和游戏服务器管理。

---

## 技术栈

| 维度 | 技术 |
|------|------|
| 框架 | Spring Boot 4.0.1 (Jakarta EE) |
| Java | 21 |
| 数据库 | PostgreSQL |
| 连接池 | HikariCP |
| 安全 | Spring Security + JWT (Hutool JWT) |
| 密码加密 | BCrypt |
| 工具库 | Hutool 5.8.32, Lombok |
| 实时通信 | WebSocket + SSE |
| 前端 | Vue 2/3 + Element UI (原生 HTML/CSS/JS，无构建工具) |

---

## 项目结构

```
src/main/java/com/example/localPicmaService/
├── config/                  # 配置类
│   ├── SystemConfig.java    # 系统配置（从 JSON 文件加载）
│   └── PasswordConfig.java  # 密码编码器
├── security/                # 安全模块
│   ├── SecurityConfig.java  # 安全过滤链配置
│   ├── JwtFilter.java       # JWT 过滤器
│   └── CustomUserDetailsService.java
├── page/                    # 页面相关 API（被 HTML 页面调用）
│   ├── auth/controller/     # 登录、注册、用户信息
│   ├── admin/controller/    # 后台管理（角色、菜单、权限）
│   ├── squad/controller/    # 战队管理
│   ├── home/                # 首页
│   ├── cartoon/controller/  # 漫画管理
│   ├── login/               # 页面路由
│   └── ResourceController.java  # 静态资源服务
├── api/                     # 纯 API（不被 HTML 页面使用）
│   ├── comic/               # 漫画相关（PicACG 客户端等）
│   └── command/             # 进程管理（游戏服务器等）
├── common/                  # 通用组件
│   ├── ZMessage.java        # 统一响应体
│   └── DramVariable.java    # 线程局部变量
└── tool/SQLTool/            # 自研 SQL 工具
    ├── SqlUtil.java         # 通用 SQL 工具类
    └── SqlUtilConfig.java   # Spring 自动配置
```

---

## API 路由规范

### 页面 API（被 HTML 页面调用）

路径格式：`/page/{页面名}/api/{功能}`

| 页面 | API 前缀 | Controller |
|------|----------|------------|
| 登录/注册 | `/page/login/api/` | `page/auth/controller/AuthController` |
| 用户信息 | `/page/login/api/user/` | `page/auth/controller/UserProfileController` |
| 后台管理 | `/page/admin/api/` | `page/admin/controller/AdminController` |
| 战队管理 | `/page/squad/api/admin/` | `page/squad/controller/SquadController` |
| 漫画管理 | `/page/cartoon/api/` | `page/cartoon/controller/CartoonController` |
| 首页数据 | `/home/api/` | `page/home/HomePageController` |

### 纯 API（不被 HTML 页面使用）

路径格式：`/api/{模块}/{功能}`

| 模块 | API 前缀 |
|------|----------|
| PicACG 漫画源 | `/api/comic/` |
| 进程管理 | `/api/executeCommand/` |

### 公开 API（免登录）

在 `SecurityConfig.java` 的 `permitAll()` 中配置。

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

### 密码存储

- 使用 BCrypt 加密存储
- 前端"记住密码"使用 XOR + Base64 混淆存储到 localStorage

---

## 数据库规范

### SQL 工具（SqlUtil）

自研 JDBC 工具，使用自定义占位符语法：

```java
// 查询
SqlUtil.query("SELECT * FROM users WHERE name = {?varchar|name?}", Map.of("name", "张三"));

// 单行查询
SqlUtil.row("SELECT * FROM users WHERE id = {?integer|id?}", Map.of("id", 1));

// 执行
SqlUtil.exec("UPDATE users SET name = {?varchar|n?} WHERE id = {?integer|id?}", Map.of("n", "李四", "id", 1));

// 表同步（自动生成 CRUD SQL）
SqlUtil.sync("my_table").insert(List.of(data)).commit();
```

占位符格式：`{?类型|参数名?}`，类型对应 `java.sql.Types` 常量名。

### 核心表结构

| 表名 | 用途 |
|------|------|
| `web_user` | 用户表 |
| `web_user_role` | 用户-角色关联 |
| `sys_role` | 角色表 |
| `sys_menu` | 菜单/页面表（支持树形结构，`is_folder` 区分文件夹和菜单） |
| `sys_role_menu` | 角色-菜单关联 |
| `manga_source` | 漫画/资源数据 |
| `squad` | 战队表 |
| `squad_member` | 战队成员表 |

---

## 前端规范

### 模块体系

每个页面引入以下公共模块：

```html
<script src="/public/res/module/auth.js"></script>        <!-- 认证工具 -->
<script src="/public/res/module/login-modal.js"></script>  <!-- 全局登录弹窗 -->
<script>LoginModal.init();</script>                         <!-- 初始化 -->
```

### auth.js 提供的方法

| 方法 | 说明 |
|------|------|
| `Auth.setAuth(token, username)` | 保存 token 到 localStorage + cookie |
| `Auth.clearAuth()` | 清除认证信息 |
| `Auth.checkToken()` | 校验 token 是否有效 |
| `Auth.validateOrAutoLogin()` | 校验 token，失败则尝试自动登录 |
| `Auth.authFetch(url, options)` | 带 token 的 fetch（401 时自动重试登录） |
| `Auth.saveCredentials(u, p, remember, auto)` | 加密保存凭据 |
| `Auth.loadCredentials()` | 解密读取凭据 |
| `Auth.logout()` | 退出登录 |

### 页面登录状态校验

页面加载时通过 `LoginModal.init()` → `Auth.validateOrAutoLogin()` 自动校验：
- token 有效 → 显示用户头像
- token 失效 + 有自动登录凭据 → 静默重新登录
- 都失败 → 显示"登录"按钮

### 静态资源路径

- 公开资源：`/public/res/module/{页面}/`
- 受保护资源：`/auth/res/module/{页面}/`
- 资源控制器：`ResourceController.java` 统一处理

---

## 配置管理

### SystemConfig

- 配置文件：`./config/SystemConfig.json`（相对于 JAR 所在目录）
- 启动时自动加载，文件不存在则创建默认配置
- 运行时可通过 `systemConfig.reload()` 热更新

JSON 结构：

```json
{
  "sys.mediaRootPath": {
    "text": "Media root path",
    "type": "String",
    "zh-cn": "媒体资源根目录",
    "value": "Z:\\bika"
  }
}
```

---

## 开发规范

### 新增页面步骤

1. **后端 Controller**：放在 `page/{页面名}/controller/` 下，路径 `/page/{页面名}/api/`
2. **页面路由**：在 `LoginRouter.java` 添加 `@GetMapping`
3. **前端 HTML**：放在 `static/module/{页面名}/` 下
4. **安全配置**：需要公开的接口在 `SecurityConfig.java` 的 `permitAll()` 中添加
5. **菜单配置**：在 `sys_menu` 表中添加记录（`is_folder` 区分文件夹和菜单）

### 新增纯 API 步骤

1. **后端 Controller**：放在 `api/{模块}/controller/` 下，路径 `/api/{模块}/`
2. **安全配置**：根据需要配置 `permitAll()` 或需要认证

### Java 注意事项

- Spring Boot 4 使用 **Jakarta EE**，包名是 `jakarta.servlet`，不是 `javax.servlet`
- `Map.of()` 最多支持 10 对键值（20 个参数），超过时使用 `LinkedHashMap`
- SQL 工具使用自定义占位符 `{?类型|参数名?}`，不是 `?` 占位符
- JSON 字段解析使用 Hutool 的 `JSONObject` / `JSONArray`

### 前端注意事项

- 页面使用 Vue 2/3 + Element UI（通过 CDN 引入）
- 登录相关操作统一通过 `auth.js` 和 `login-modal.js`
- 图片等资源通过 ID 请求后端代理，不在 URL 中暴露文件路径
- `AUTH_TOKEN` cookie 和 localStorage 中的 `token` 必须同步更新

---

## 目录结构映射（漫画/资源）

```
{mediaRootPath}/{type映射}/{path}/{title}/cover.jpg       ← 封面
{mediaRootPath}/{type映射}/{path}/{title}/{章节index}/1.jpg  ← 章节图片
```

- `type=漫画` → 目录名 `cartoon`
- `type=coser` → 目录名 `coser`
- 章节信息存储在 `manga_source.chapters` 字段（JSON 数组）
