# 联智·智能考勤管理系统

企业级 AI 人脸识别考勤系统（北游 JavaEE 工程实践项目）

## 技术栈

- 后端：Spring Boot 2.7 + MyBatis-Plus + Druid + MySQL 8 + Redis + ZXing（二维码）
- 前端：Vue 3 + Vite + ElementPlus + Pinia + Vue Router + Axios + ECharts
- 人脸识别：虹软 ArcSoft SDK（第 2 周接入）

## 目录结构

```
lianzhi-attendance/
├── sql/V1__init.sql      # 数据库脚本（7张表 + 初始管理员）
├── docs/                 # 数据库设计文档、两周作战计划
├── server/               # SpringBoot 后端（端口 8080）
└── admin-web/            # Vue 管理端（端口 5173）
```

## 启动步骤（严格按顺序）

### 1. 环境准备

| 软件 | 要求 | 备注 |
|---|---|---|
| JDK | 17 | `java -version` 验证 |
| IDEA | 任意版本 | 内置 Maven，不用单独装 |
| MySQL | 8.x | 记住 root 密码 |
| Redis | Windows 版 | 解压后双击 `redis-server.exe` 即可 |
| Node.js | 18+ | `node -v` 验证 |

### 2. 建数据库

用 Navicat 或命令行执行 `sql/V1__init.sql` 全部内容。
初始账号：**admin / 123456**

### 3. 启动后端

1. 用 IDEA 打开 `server` 目录，等待 Maven 自动下载依赖（第一次约 5-10 分钟）
2. 打开 `src/main/resources/application.yml`，把数据库密码改成你自己的
3. **先启动 Redis**（不启动 Redis 登录会报错！）
4. 运行 `AttendanceApplication.java` 的 main 方法
5. 看到 `启动成功，端口 8080` 即成功

### 4. 启动前端

```bash
cd admin-web
npm install     # 第一次需要下载依赖
npm run dev
```

浏览器打开 http://localhost:5173 ，用 admin/123456 登录。

## 已实现功能（骨架版）

- 登录/退出（token + Redis 会话）
- 数据中心：今日打卡概览 4 卡片 + 2 个 ECharts 图（部分演示数据）
- 办公场所：增删改查 + 分页搜索 + 场所二维码生成
- 员工管理：列表查询、按场所筛选、人脸照片展示
- 打卡记录：按日期/场所筛选查询
- 文件上传接口（人脸照片用）

## 待开发（见 docs/两周全功能作战计划.md）

H5 员工采集、人脸比对打卡、活体检测、Spring Task 定时统计、热力图、请假审批、uni-app 小程序。

## 常见问题

| 报错 | 原因 |
|---|---|
| 登录提示"网络错误" | Redis 没启动 |
| 后端启动报 Access denied | application.yml 里数据库密码不对 |
| 前端页面 404 接口 | 后端没启动，或 vite.config.js 代理没生效 |
| npm install 很慢 | 换淘宝镜像：`npm config set registry https://registry.npmmirror.com` |
