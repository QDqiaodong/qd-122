# 装配机标准弹簧产线归属划转管理系统

## 项目简介

装配车间弹簧零部件产线归属划转系统，支持弹簧档案、初始产线绑定、跨产线划转、历史轨迹查询和产线汇总，并提供产线负载预警看板。

## 负载预警看板

首页「产线负载预警」为每条产线维护日承载阈值与适用弹力系数区间，按以下维度计算负载状态（正常 / 预警 / 超载）：

- 数量负载：当前归属弹簧数量对比日承载阈值（>100% 超载，=100% 或 ≥80% 预警）；
- 弹力系数区间：归属弹簧中存在系数超出该产线适用区间的，触发预警；
- 划转趋势：近 7 天持续净流入且负载率已 ≥80%，提示短期超载风险。

看板顶部统计与正常/预警/超载分组来自同一份接口数据，刷新后统计与列表始终一致；点击产线卡片可查看归属弹簧明细、全部触发原因与近期划转流水，并可在线维护阈值配置。

## 技术栈

- 前端：Vue 3、Vite 5、TypeScript、Element Plus、Pinia、Axios
- 后端：Spring Boot 3、JDK 17、Spring Data JPA、Spring Data Redis、Maven
- 数据：MySQL 8、Redis 7
- 部署：Docker Compose、Nginx

## 端口说明

| 服务 | 地址 |
| --- | --- |
| 前端 | http://localhost:3122 或 http://127.0.0.1:3122 |
| 后端 API | http://127.0.0.1:8122/api |
| MySQL | 127.0.0.1:3422 |
| Redis | 127.0.0.1:6422 |

端口来自根目录 `.env`，Docker 端口只绑定 `127.0.0.1`。

## 启动方式

```bash
cd qd-122
docker compose up -d --build
```

本地拆分验证：

```bash
cd backend
mvn compile -q

cd ../frontend
npm ci
npm run build
```

## Docker 构建说明

前端按锁文件安装依赖并生产构建；后端按 Maven 编译打包；MySQL 与 Redis 由 `docker-compose.yml` 编排。整体验证使用：

```bash
docker compose up -d --build
docker compose ps
```

## 常见问题

- `mvn compile -q` 失败：先确认 JDK 17、Lombok 和 `maven-compiler-plugin` annotation processor 配置。
- `npm run build` 失败：按 TypeScript、import 路径或 Vite 报错修复。
- build 已通过但页面接口失败：检查后端容器、数据库初始化和 Nginx `/api` 代理链路。
