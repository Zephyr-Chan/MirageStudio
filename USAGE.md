# MirageStudio 使用手册

## 一、环境准备

### 1.1 必需软件

| 软件 | 最低版本 | 用途 |
|------|----------|------|
| Docker Desktop | 4.20+ | 运行 MySQL/Redis/MinIO/ComfyUI |
| JDK | 17 | Java 后端 |
| Maven | 3.9+ | Java 依赖管理 |
| Node.js | 18+ | 前端构建 |
| Python | 3.10+ | GPU Worker |
| Git | 2.40+ | 版本管理 |

### 1.2 可选软件（GPU 相关）

- NVIDIA Driver (CUDA 11.8+) — 3DGS 重建和 ComfyUI 特效需要
- COLMAP — 照片转 3D 点云（无 GPU 时 Worker 自动切 Mock 模式）

### 1.3 验证环境

```powershell
java -version     # 应显示 17.x
mvn -version      # 应显示 3.9+
node -v           # 应显示 v18+
python --version  # 应显示 3.10+
docker version    # 应显示 Server: 24+
```

---

## 二、启动服务（按顺序）

### 第 1 步：启动基础设施（Docker）

```powershell
cd e:\Excellent
docker-compose up -d mysql redis minio minio-init
```

等待 10-15 秒让 MySQL 完成初始化（首次启动会自动执行 `schema.sql` 建表）。

验证：
```powershell
docker ps  # 应看到 mirage-mysql, mirage-redis, mirage-minio 三个容器
```

| 服务 | 地址 | 账号/密码 |
|------|------|-----------|
| MySQL | localhost:3306 | root / mirage123 |
| Redis | localhost:6379 | 无密码 |
| MinIO Console | http://localhost:9001 | mirage / mirage12345 |
| MinIO API | http://localhost:9000 | 同上 |

### 第 2 步：启动 Java 后端

```powershell
cd e:\Excellent\mirage-backend
mvn clean install -DskipTests
```

编译成功后启动 Web 服务：
```powershell
cd mirage-web
mvn spring-boot:run
```

验证：浏览器打开 http://localhost:8080/api/auth/login ，应返回 JSON 错误（说明服务已启动，只是请求方法不对）。

> 后端运行在 `localhost:8080`，提供 `/api/**` REST 接口和 `/ws` WebSocket 端点。

### 第 3 步：启动前端

```powershell
cd e:\Excellent\mirage-frontend
npm install        # 首次需要安装依赖
npm run dev
```

验证：浏览器打开 http://localhost:5173 ，应看到登录页面。

### 第 4 步（可选）：启动 Python GPU Worker

```powershell
cd e:\Excellent\mirage-gpu
pip install -r requirements.txt

# 终端 1：启动重建 Worker
python -m recon_worker.main

# 终端 2：启动特效 Worker
python -m effect_worker.main
```

> 无 GPU 时 Worker 会自动切 Mock 模式：重建生成占位 .ply 文件，特效生成占位图片。端到端流程仍可走通。

### 第 5 步（可选）：启动 ComfyUI

```powershell
cd e:\Excellent
docker-compose up -d comfyui
```

> ComfyUI 需要 NVIDIA GPU。无 GPU 时跳过，特效 Worker 会用 Mock 模式。

---

## 三、注册与登录

### 3.1 注册新账号

1. 打开 http://localhost:5173
2. 在登录页点击「注册」切换到注册表单
3. 填写：
   - **用户名**：3-32 位字母数字（如 `testuser`）
   - **邮箱**：可选（如 `test@example.com`）
   - **密码**：6-32 位（如 `123456`）
4. 点击「注册」按钮
5. 注册成功后自动登录并跳转到项目工作台

> 也可以用 curl 测试：
> ```powershell
> curl -X POST http://localhost:8080/api/auth/register `
>   -H "Content-Type: application/json" `
>   -d '{"username":"testuser","password":"123456","email":"test@test.com"}'
> ```
> 预期返回：`{"code":0,"message":"注册成功","data":{"userId":...,"username":"testuser","role":"USER","token":"..."},"success":true}`

### 3.2 登录

1. 在登录页输入用户名和密码
2. 点击「登录」
3. 成功后跳转到 `/projects` 项目工作台

> Token 存储在 localStorage 的 `mirage_token` 键中，刷新页面不会丢失登录状态。

### 3.3 登录失败排查

| 症状 | 原因 | 解决 |
|------|------|------|
| "网络连接失败" | 后端未启动 | 确认 `localhost:8080` 可访问 |
| "用户名或密码错误" | 密码不对 | 重新注册或检查密码 |
| 页面白屏 | 前端依赖未安装 | 运行 `npm install` |
| CORS 错误 | 直连后端绕过 Vite 代理 | 确保通过 `localhost:5173` 访问，不要直接访问 `localhost:8080` |
| "请求超时" | 后端启动慢 | 等待 Spring Boot 完全启动（日志出现 "Started MirageStudioApplication"） |

---

## 四、功能使用

### 4.1 创建项目

1. 在项目工作台 (`/projects`) 点击「新建项目」
2. 输入项目名称（如 "我的房间 3D"）
3. 点击确定，项目卡片出现在列表中
4. 点击项目卡片进入项目

### 4.2 3D 重建（照片→3D 场景）

1. 进入项目后，点击「3D 重建」或导航到 `/projects/:id/reconstruct`
2. **上传照片**：
   - 拖拽 5-200 张照片到上传区域，或点击选择文件
   - 点击「开始上传」
   - 等待所有照片上传完成（进度条 100%）
3. **配置参数**：
   - 迭代次数：7000（快速）/ 15000（标准）/ 30000（高质量）
   - 分辨率比例：1（原始）/ 2（降采样）
4. 点击「开始重建」
5. 等待重建完成：
   - Mock 模式：几秒内完成
   - GPU 模式：5-30 分钟（取决于照片数和迭代次数）
   - 进度条和日志实时更新
6. 重建完成后，点击「进入工作室查看」跳转到 3D 查看器

### 4.3 3D 工作室

工作室页面 (`/projects/:id/studio`) 是核心交互页面，包含四个面板：

| 面板 | 位置 | 功能 |
|------|------|------|
| Agent 面板 | 左侧 | 自然语言对话（阶段二功能，MVP 阶段显示占位） |
| 3D 查看器 | 中间 | 拖拽旋转、滚轮缩放、右键平移 |
| 特效编辑器 | 右侧 | 选择模板、输入 Prompt、调整参数、应用特效 |
| 时间轴 | 底部 | 相机关键帧（MVP 阶段占位） |

**3D 查看器操作**：
- 鼠标左键拖拽：旋转视角
- 鼠标滚轮：缩放
- 鼠标右键拖拽：平移
- 截屏按钮：截取当前视角，自动上传作为特效输入

### 4.4 应用 AI 特效

1. 在 3D 查看器中调整到满意的角度
2. 点击截屏按钮（相机图标）
3. 在右侧特效编辑器中：
   - 选择模板（如「赛博朋克风格化」）
   - 输入风格提示词（如 `cyberpunk city, neon lights, rain`）
   - 调整随机种子（控制生成多样性）
   - 调整 ControlNet 强度（0-2，越高越贴合原图结构）
4. 点击「生成特效」
5. 等待生成完成：
   - Mock 模式：几秒内完成
   - ComfyUI 模式：10-60 秒
6. 生成完成后在下方查看前后对比

### 4.5 任务监控

导航到 `/tasks` 查看所有任务状态：
- 重建任务和特效任务合并展示
- 实时进度通过 WebSocket 推送
- 任务状态：PENDING → QUEUED → RUNNING → SUCCESS / FAILED

---

## 五、架构说明

### 5.1 技术栈分层

```
前端 (Vue3 + TS)  →  Vite Proxy  →  Java 后端 (Spring Boot)
                                      ↓
                              Redis Streams (任务队列)
                                      ↓
                          Python GPU Worker (重建/特效)
                                      ↓
                              MinIO (3D 资产存储)
```

### 5.2 API 路径映射

| 前端调用 | 实际后端端点 | 说明 |
|----------|-------------|------|
| `POST /api/auth/login` | AuthController | 登录 |
| `POST /api/auth/register` | AuthController | 注册 |
| `GET /api/projects` | ProjectController | 项目列表 |
| `POST /api/projects` | ProjectController | 创建项目 |
| `POST /api/assets/upload` | AssetController | 上传照片/截屏 |
| `POST /api/recon` | ReconstructionController | 提交重建任务 |
| `GET /api/recon?projectId=` | ReconstructionController | 重建任务列表 |
| `POST /api/effects` | EffectController | 提交特效任务 |
| `GET /api/workflows` | WorkflowController | 特效模板列表 |
| `GET /api/tasks/{id}` | TaskController | 任务实时状态 |
| `WS /ws` | WebSocketConfig | STOMP 任务进度推送 |

### 5.3 统一响应格式

所有 API 返回统一 `R<T>` 格式：
```json
{
  "code": 0,
  "message": "操作描述",
  "data": { ... },
  "success": true
}
```

前端 Axios 拦截器自动解包：`response.data.data` → 直接返回 `data` 字段内容。

---

## 六、常见问题

### Q: 后端启动报 "Failed to configure a DataSource"？

A: MySQL 未启动或密码不对。检查：
1. `docker ps` 确认 `mirage-mysql` 容器运行中
2. `application-dao.yml` 中密码为 `mirage123`（与 docker-compose 一致）
3. MySQL 初始化需要 10-15 秒，等容器 healthcheck 通过再启动后端

### Q: 前端登录提示 "网络连接失败"？

A: 后端未运行或端口被占用。检查：
1. 确认 `http://localhost:8080` 可访问
2. 检查 8080 端口是否被其他程序占用：`netstat -ano | findstr :8080`
3. 查看 Java 后端控制台是否有异常

### Q: 上传照片后看不到预览？

A: MinIO 未正确配置。检查：
1. `docker ps` 确认 `mirage-minio` 运行中
2. 打开 http://localhost:9001 用 `mirage` / `mirage12345` 登录
3. 确认有 `mirage-photos` 桶（由 `minio-init` 容器自动创建）

### Q: 重建任务一直 PENDING？

A: Python Worker 未启动。检查：
1. 启动 `python -m recon_worker.main`
2. Worker 日志应显示 "等待任务..."
3. Worker 连接 Redis 的地址应为 `localhost:6379`

### Q: WebSocket 连不上？

A: 端点路径不匹配。检查：
1. 前端连接 `ws://localhost:5173/ws`（Vite 代理转发到 8080）
2. 后端 WebSocket 端点为 `/ws`（已移除 SockJS）
3. SecurityConfig 中 `/ws/**` 已 `permitAll()`

### Q: 如何重置数据库？

A:
```powershell
docker-compose down -v   # -v 删除数据卷
docker-compose up -d mysql redis minio minio-init
```

### Q: 如何查看后端日志？

A: 后端控制台直接输出。或添加 `--debug` 参数：
```powershell
cd mirage-backend\mirage-web
mvn spring-boot:run -Dspring-boot.run.arguments=--debug
```

---

## 七、开发指南

### 7.1 项目结构

```
e:\Excellent\
├── mirage-backend/          # Java 后端 (7 个 Maven 模块)
│   ├── mirage-gateway/      # Spring Cloud Gateway
│   ├── mirage-common/       # 公共类 (DTO/异常/常量)
│   ├── mirage-dao/          # 数据访问 (MyBatis-Plus + schema.sql)
│   ├── mirage-service/      # 业务逻辑
│   ├── mirage-task/         # 任务调度 (Redis Streams + GPU 槽位)
│   ├── mirage-integration/  # 外部集成 (ComfyUI/MinIO)
│   └── mirage-web/          # Web 层 (Controller/JWT/WebSocket)
├── mirage-gpu/              # Python GPU 微服务
│   ├── common/              # 公共 (Stream 消费者/槽位/状态回写)
│   ├── recon_worker/        # 3DGS 重建
│   └── effect_worker/       # ComfyUI 特效
├── mirage-frontend/         # Vue3 + TS 前端
│   └── src/
│       ├── views/           # 5 个页面
│       ├── components/      # 5 个组件
│       ├── stores/          # Pinia (auth/project/task)
│       └── api/             # Axios API 层
├── docker-compose.yml       # 基础设施编排
├── nginx.conf               # Nginx 反向代理
└── README.md
```

### 7.2 后端开发

新增 API 端点示例：
1. 在 `mirage-dao` 中新增 Entity + Mapper
2. 在 `mirage-service` 中新增 Service 方法
3. 在 `mirage-web/controller` 中新增 Controller
4. 返回 `R.ok(data)` 或 `R.fail(message)`

### 7.3 前端开发

新增页面示例：
1. 在 `src/views/` 中新增 `XxxView.vue`
2. 在 `src/router/index.ts` 中注册路由
3. 在 `src/api/` 中新增 API 调用（使用 `client` 实例）
4. 响应数据已被 `client.ts` 拦截器自动解包

### 7.4 Python Worker 开发

新增 Worker：
1. 继承 `common.stream_consumer.StreamConsumer`
2. 实现 `handle_message(task_data)` 方法
3. 在 `main.py` 中注册消费组并启动

---

## 八、后续阶段规划

| 阶段 | 内容 | 对应岗位 |
|------|------|----------|
| 阶段二 | AI Agent 智能层 (Python LangGraph + MCP Server) | AI Agent 开发 |
| 阶段三 | 算法层 (LoRA/ControlNet 微调 + 时序一致性) | AIGC 算法 |
| 阶段四 | 传统后端加固 (分库分表 + 压测 + 可观测) | 传统后端 |
