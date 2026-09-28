# localPicmaService

一个完全由 AI 构建的本地多功能服务端，基于 Spring Boot 4 + Java 21 + PostgreSQL。

## 主要功能

| 模块 | 路径 | 说明 |
|------|------|------|
| 漫画浏览 | `/cartoon` | 推荐/搜索/收藏/章节阅读 |
| 漫画后台 | `/admin/manga` | 管理/SQLite导入/章节压缩/去重 |
| 个人网站 | `/home` | 游戏日志/摄影/生活/工作 |
| 首页管理 | `/home/admin` | 内容管理/多图上传 |
| FRP 控制 | `/frp` | 客户端实例/代理规则/配置生成 |
| 系统管理 | `/system` | 系统配置/文件管理 |
| 用户管理 | `/admin` | 用户/角色/菜单 |

## 技术栈

- **后端**: Spring Boot 4.1.0 / Java 21 / PostgreSQL / Valkey / Caffeine / RustFS
- **前端**: Vue 3 / Element Plus 2.14.4 / Axios（无构建工具）
- **安全**: Spring Security + JWT

## 快速开始

```bash
# 启动（端口 8085）
./mvnw spring-boot:run
```

访问 `http://localhost:8085` 即可。

## 项目文档

- `rule.md` — 开发规范、API 路由、数据库设计、常见问题
- `KNOWLEDGE_BASE.md` — 完整知识库（架构、组件、配置、故障排查）
- `config/frpc_example.json` — FRP 客户端配置示例
