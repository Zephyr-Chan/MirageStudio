# MirageStudio 幻境工坊

> AI Agent 驱动的 3D 内容创作平台 — 融合 3D Gaussian Splatting 照片转 3D 场景重建 + AI 电影级特效生成

## 项目架构

四层架构，每层使用该岗位主流技术栈，精准对应五类面试岗位：

```
┌─────────────────────────────────────────────────────────┐
│              前端 SPA (Vue3 + TS + Three.js)              │
│  3D查看器 │ 特效编辑器 │ Agent对话/轨迹面板 │ 时间轴       │
└──────────┬──────────────────────────┬────────────────────┘
           │ HTTP (经 Gateway)         │ WebSocket(STOMP)
┌──────────▼──────────┐  ┌───────────▼────────────────────┐
│ Spring Cloud Gateway│  │  AI Agent 智能层 (Python)       │
│ 路由/限流/JWT鉴权    │  │  LangChain/LangGraph + MCP SDK │
└──────────┬──────────┘  └───────────┬────────────────────┘
┌──────────▼──────────────────────────▲────────────────────┐
│  Java Spring Boot 后端 (编排层)                            │
│  Project/Asset/Recon/Effect Service │ TaskDispatcher       │
│  GpuSlotManager(秒杀抢槽) │ ComfyUiClient │ MyBatis-Plus   │
└──────────┬──────────────────────────┬────────────────────┘
           │ Redis Streams             │ 状态回写
┌──────────▼──────────────────────────▼────────────────────┐
│  Python GPU 执行层                                        │
│  3DGS重建Worker │ ComfyUI特效Worker │ 微调训练Worker       │
└──────────┬──────────────────────────┬────────────────────┘
     MySQL(元数据)  Redis(队列+缓存)  MinIO(3D资产)
```

## 技术栈

| 层 | 技术栈 | 对应岗位 |
|---|---|---|
| 传统后端 | Java Spring Boot + Spring Cloud Gateway + MyBatis-Plus + Redis + MySQL + RocketMQ + ShardingSphere | 传统后端开发 |
| AIGC编排 | Java Spring Boot (ComfyUI HTTP/WS 编排, 异步GPU管线, 语义缓存) | AI 后端开发 |
| AI Agent | Python LangChain/LangGraph + MCP Python SDK + FastAPI | AI Agent 开发 |
| AIGC算法 | Python PyTorch + diffusers/kohya (LoRA/ControlNet微调) | AIGC算法/算法实习 |

## 项目结构

```
e:\Excellent\
├── mirage-backend/          # Java Spring Boot 多模块后端
│   ├── mirage-gateway/      # Spring Cloud Gateway (路由/限流/鉴权)
│   ├── mirage-common/       # 公共: DTO/异常/常量/Redis key/状态机
│   ├── mirage-dao/          # MyBatis-Plus Entity/Mapper + schema.sql
│   ├── mirage-service/      # 业务Service (Project/Asset/Recon/Effect/User)
│   ├── mirage-task/         # 任务调度 (TaskDispatcher/GpuSlotManager/TaskStatusStore)
│   ├── mirage-integration/  # 外部集成 (ComfyUiClient/MinioClient/WorkflowRenderer)
│   └── mirage-web/          # Web层 (Controller/JWT/WebSocket/全局异常)
├── mirage-agent/            # Python AI Agent 服务 (阶段二)
├── mirage-gpu/              # Python GPU 微服务
│   ├── common/              # Stream消费者/GPU槽位/状态回写/MinIO客户端
│   ├── recon_worker/        # 3DGS重建 (COLMAP→train.py→.splat)
│   └── effect_worker/       # ComfyUI特效 (模板渲染→/prompt→WS→/view)
├── mirage-frontend/         # Vue3+TS 前端
│   └── src/
│       ├── views/           # 登录/项目工作台/重建向导/工作室/任务监控
│       ├── components/      # SplatViewer/EffectEditor/AgentPanel/Timeline
│       ├── stores/          # Pinia (auth/project/task with STOMP)
│       └── api/             # Axios API层
├── docker-compose.yml       # MySQL+Redis+MinIO+ComfyUI+Nginx
├── nginx.conf               # Nginx反向代理 (MinIO Range/API/WS)
└── .gitignore
```

## 快速开始

### 1. 启动基础设施

```bash
docker-compose up -d mysql redis minio minio-init nginx
```

### 2. 启动 Java 后端

```bash
cd mirage-backend
mvn clean install -DskipTests
cd mirage-web
mvn spring-boot:run
# 后端运行在 http://localhost:8080
```

### 3. 启动 Python GPU Worker

```bash
cd mirage-gpu
pip install -r requirements.txt

# 启动重建Worker
python -m recon_worker.main

# 启动特效Worker (另开终端)
python -m effect_worker.main
```

### 4. 启动前端

```bash
cd mirage-frontend
npm install
npm run dev
# 前端运行在 http://localhost:5173
```

### 5. (可选) 启动 ComfyUI

```bash
docker-compose up -d comfyui
# ComfyUI运行在 http://localhost:8188
```

## MVP 端到端链路

1. 注册/登录 → 创建项目
2. 上传照片 → 触发 3DGS 重建 (COLMAP→train.py→.splat)
3. 浏览器中 3D 漫游重建场景
4. 选视角截屏 → 应用赛博朋克风格特效 (ComfyUI)
5. 查看特效结果对比

## 开发阶段

- **阶段一 MVP** (当前): 端到端最小闭环 + 基础后端
- **阶段二**: Agent 智能层 (Python LangGraph + MCP Server)
- **阶段三**: 算法层 (LoRA/ControlNet微调 + 时序一致性模块)
- **阶段四**: 传统后端加固 (分库分表 + 压测 + 可观测)

## License

MIT
