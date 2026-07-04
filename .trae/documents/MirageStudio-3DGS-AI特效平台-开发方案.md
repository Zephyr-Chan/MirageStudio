# MirageStudio 幻境工坊 — AI Agent 驱动的 3D 内容创作平台 开发方案（v3）

## 一、概述（Summary）

**项目定位**：一个由 AI Agent 自主编排的 3D 内容创作平台，融合「3D Gaussian Splatting 照片转 3D 场景重建」+「AI 电影级特效生成」+「算法实验模块」。完整链路：用户用自然语言下达创作目标（如"把我的房间照片变成赛博朋克风格的 3D 漫游短片"）→ **AI 导演 Agent** 自主规划并调用工具（重建/特效/渲染/剪辑）→ 产出可漫游 3D 场景与渲染视频。

**为什么是四层架构**：单一项目要同时支撑五类岗位投递，必须让每一层都精准对应一类岗位的考察点，且**每层使用该岗位主流技术栈**（而非一个语言打天下）。本方案据此设计为四层：

| 架构层 | 语言/框架 | 对应投递岗位 | 该层核心能力 |
|---|---|---|---|
| 传统后端编排层 | **Java** Spring Boot + Spring Cloud Gateway + MyBatis-Plus + Redis + MySQL + RocketMQ + ShardingSphere | 传统后端开发 | 高并发 GPU 抢槽、分库分表、分布式锁、限流、读写分离、微服务网关 |
| AIGC 服务编排层 | **Java** Spring Boot（ComfyUI HTTP/WS 编排） | AI 后端开发 | ComfyUI/LLM API 编排、异步 GPU 管线、语义缓存、presigned、STOMP |
| AI Agent 智能层 | **Python** LangChain/LangGraph + MCP Python SDK | AI Agent 开发 | ReAct 规划、工具调用、**MCP Server 暴露**、Agent 记忆与反思 |
| AIGC 算法层 | **Python** PyTorch + diffusers/kohya | AIGC 算法 / 算法实习 | LoRA/ControlNet **微调训练**、时序一致性工程模块、消融实验 |

> **v3 关键修正**：v2 的 Agent 层使用 Java（LangChain4j），但全网 JD 调研显示 AI Agent 开发岗**以 Python 为绝对主力**（LangChain/LangGraph/MCP SDK 均为 Python 优先生态），Java Agent 框架匹配度明显偏低 [$TRAE_REF](https://m.zhipin.com/job_detail/fbf249d3f198668e03xz2du7GVBT.html)[$TRAE_REF](https://m.zhipin.com/job_detail/86da55d216b1257003153tS8GFFU.html)。v3 将 Agent 层改为 Python 实现，与 Java 后端通过 HTTP/MCP 协议通信，既保证 Agent 岗技术栈匹配，又保留 Java 后端工程深度。

面试时按目标岗位强调对应层即可。大厂明确看重"中间件+云原生+AI 融合"整合能力与独立 AI 项目经验 [$TRAE_REF](https://blog.csdn.net/2402_84764726/article/details/156110401)[$TRAE_REF](https://jobs.bytedance.com/campus/m/position/detail/7483480177587833106)；AIGC 算法岗明确要求 LoRA/ControlNet 微调与可控生成能力，**以微调为主流要求而非从零预训练** [$TRAE_REF](https://m.zhipin.com/job_detail/702c8d996397e62003Fz0tu9FFVR.html)[$TRAE_REF](https://m.zhipin.com/job_detail/94bdfdcc3ef81a7103d42du9E1tR.html)；算法实习岗论文为加分项非硬性门槛，强项目+强代码可替代 [$TRAE_REF](https://m.zhipin.com/job_detail/f8fb3b54c121737b0nR63dS-FVNU.html)[$TRAE_REF](https://m.zhipin.com/job_detail/271cf3145e87304b03F-2tS1FFtY.html)。

**核心技术栈**：
- Java 后端：Spring Boot + Spring MVC + Spring Cloud Gateway + MyBatis-Plus + MySQL + Redis + RocketMQ + ShardingSphere
- AI Agent（Python 独立服务）：LangChain/LangGraph + MCP Python SDK（自建 MCP Server）+ FastAPI
- Python GPU 微服务：gaussian-splatting 重建 + ComfyUI 特效 + diffusers/kohya 微调训练
- 前端：Vue3 + TS（GaussianSplats3D 3D 查看器 + 特效编辑器 + Agent 对话/轨迹面板）

---

## 二、岗位覆盖度分析（Reasonableness Review）

> 本节正面回应"该方案能否帮助面试五类岗位"。基于全网 JD 调研（2025-2026 真实招聘页面），逐岗对照核心考察点给出诚实评估。

### 2.1 覆盖度矩阵

| 岗位 | JD 核心要求（调研） | v2 覆盖 | v3 修正 | 面试可讲点 | 残留差距 |
|---|---|---|---|---|---|
| 传统后端开发 | Java/JVM、Spring 全家桶、MySQL 调优、Redis 高级、MQ、分布式锁、限流、分库分表 [$TRAE_REF](https://m.zhipin.com/job_detail/5890dfdb6906ff5203V93tu1FVBR.html)[$TRAE_REF](https://blog.csdn.net/2402_84764726/article/details/156807998) | 中上 | **强** | GPU 抢槽秒杀式高并发、Redis Streams+Lua、RocketMQ 事件流、ShardingSphere 分库分表、Spring Cloud Gateway 网关、MySQL 索引调优/读写分离 | JVM 调优需实战积累；Spring Cloud 全家桶（Nacos/Feign/Sentinel）可在面试中讨论设计但不必全实现 |
| AI 后端开发 | 后端工程+AIGC 编排、多模态模型调用框架、任务调度、AI 网关 [$TRAE_REF](https://m.zhipin.com/job_detail/e61048b00ae699a01X1509q4FFVV.html)[$TRAE_REF](https://m.yupao.com/zhaogong/384320098.html) | 强 | **强** | ComfyUI HTTP/WS 编排、异步 GPU 管线、语义缓存降本、presigned 资产分发、STOMP 推送、多模板工作流引擎 | vLLM/Triton 推理部署为加分项，方案中以 ComfyUI 为推理后端，可在技术报告中对比讨论 |
| AI Agent 开发 | **Python**、LangChain/LangGraph、MCP、工具调用、RAG、多 Agent [$TRAE_REF](https://m.zhipin.com/job_detail/fbf249d3f198668e03xz2du7GVBT.html)[$TRAE_REF](https://m.zhipin.com/job_detail/86da55d216b1257003153tS8GFFU.html) | **弱**（Java 框架错配） | **强**（v3 改 Python） | Python LangChain/LangGraph ReAct 规划、MCP Python SDK 暴露平台能力、Agent 分层记忆与反思、工具调用全链路 | 多 Agent 协作（A2A）为加分项，可在 v3 基础上扩展 |
| AIGC 算法实习 | **微调为主**（LoRA/ControlNet）、可控生成、扩散模型原理、PyTorch 代码能力 [$TRAE_REF](https://m.zhipin.com/job_detail/94bdfdcc3ef81a7103d42du9E1tR.html)[$TRAE_REF](https://m.zhipin.com/job_detail/702c8d996397e62003Fz0tu9FFVR.html) | **中下** | **中上** | 自训风格 LoRA（rank/lr/steps 调参）、ControlNet 视角控制微调、可控生成流水线、视频特效、消融实验 | 面试会考扩散模型原理（DDPM/DDIM），需额外准备理论八股；不要求从零预训练 |
| 算法实习 | 研究+代码、论文为**加分项非硬性**、项目/开源/竞赛可替代 [$TRAE_REF](https://m.zhipin.com/job_detail/f8fb3b54c121737b0nR63dS-FVNU.html)[$TRAE_REF](https://m.zhipin.com/job_detail/271cf3145e87304b03F-2tS1FFtY.html) | **中下** | **中** | 时序一致性工程模块+消融对比、可复现研究代码、技术报告、系统化评测 | 无论文是劣势但非硬伤；"工程模块"非"学术创新"，需诚实定位为"工程平台上的算法实验"而非论文级贡献 |

### 2.2 诚实定位

- **强匹配（可直接作为面试主打项目）**：传统后端开发、AI 后端开发、AI Agent 开发 — 这三类岗位的技术栈与考察点与 v3 方案高度吻合。
- **中匹配（作为加分项，需配合其他材料）**：AIGC 算法实习 — 微调能力匹配，但面试会深问扩散模型原理，需额外准备理论。
- **弱匹配（诚实评估）**：纯算法实习 — 本项目的算法层是"工程平台上的实验性模块"，非论文级学术贡献。投递算法岗时建议**以代码能力+工程化实验为主线**，不要过度包装为"自研创新算法"。论文在多数算法实习 JD 中为加分项非硬性门槛 [$TRAE_REF](https://m.zhipin.com/job_detail/f8fb3b54c121737b0nR63dS-FVNU.html)，强项目+强代码可替代。

### 2.3 v2 → v3 修正总结

| 问题 | v2 状态 | v3 修正 | 依据 |
|---|---|---|---|
| Agent 层语言错配 | Java（LangChain4j） | **Python**（LangChain/LangGraph + MCP Python SDK） | AI Agent JD 以 Python 为绝对主力 [$TRAE_REF](https://m.zhipin.com/job_detail/fbf249d3f198668e03xz2du7GVBT.html) |
| 算法层过度包装 | "自研新颖贡献" | "工程实验模块"（诚实定位） | 无导师/无论文的独立项目不宜包装为学术创新 |
| 缺少微服务网关 | 纯单体 Spring Boot | 新增 Spring Cloud Gateway | 传统后端面试常问网关/限流/路由 [$TRAE_REF](https://blog.csdn.net/2402_84764726/article/details/156807998) |
| 缺少推理部署讨论 | 仅 ComfyUI | 补充 vLLM/Triton 对比讨论 | AI 后端 JD 加分项 [$TRAE_REF](https://m.yupao.com/zhaogong/384320098.html) |

---

## 三、调研基础与现状分析（Current State Analysis）

技术选型均经全网调研核实，关键事实：

1. **3DGS 重建流程**：照片→COLMAP（SfM 位姿+稀疏点云）→`train.py` 高斯训练（30000 迭代+密度化）→`point_cloud.ply`→转 `.splat/.ksplat`，1080p/30fps 实时渲染 [$TRAE_REF](https://blog.csdn.net/level_code/article/details/135845812)[$TRAE_REF](https://blog.csdn.net/qq_65436507/article/details/152274177)。
2. **浏览器 3DGS 渲染**：`GaussianSplats3D`（Three.js，MIT）`Viewer`+`addSplatScene()`+`start()`，支持渐进式加载 [$TRAE_REF](https://blog.csdn.net/gitblog_07161/article/details/149010835)。**风险**：已停更，方案以适配器接口隔离、预留 Spark 迁移。
3. **ComfyUI API**：`POST /prompt`（workflow JSON+client_id→prompt_id）、`POST /upload/image`、`GET /history`、`GET /view`、`POST /interrupt`、WebSocket 推 `progress/executing/executed/execution_error` [$TRAE_REF](https://blog.csdn.net/2301_80471322/article/details/145716350)[$TRAE_REF](https://blog.csdn.net/kkk56/article/details/149610571)。
4. **Redis Streams 可靠队列**：`XADD`+`XREADGROUP`+`XACK`+`XPENDING/XCLAIM`（PEL），持久化+消费组+ACK，契合 GPU 可靠调度 [$TRAE_REF](https://cloud.tencent.cn/developer/article/2526953)。
5. **MCP 协议**：Anthropic 开放标准，Agent 连接外部工具的"USB-C"，2025 生态爆发，大厂布局 Agent 平台 [$TRAE_REF](https://blog.csdn.net/universsky2015/article/details/146528227)。
6. **AIGC 微调方法**：LoRA/ControlNet/DreamBooth 是 AIGC 微调主流，岗位明确要求具备训练/微调能力 [$TRAE_REF](https://m.zhipin.com/job_detail/702c8d996397e62003Fz0tu9FFVR.html)[$TRAE_REF](https://blog.csdn.net/summerriver1/article/details/148091368)。
7. **Redis 最佳实践（redis-development 技能）**：按场景选结构、统一 key、内存上限+淘汰、缓存 TTL、避免慢命令、连接池/管线、Streams 做持久队列而 Pub/Sub 仅通知、向量检索 HNSW/FLAT、语义缓存、ACL、可观测。

**项目现状**：全新项目，工作目录 `e:\Excellent` 下新建四个子项目。

---

## 四、系统架构（Proposed Architecture）

四层架构，核心思想：**Agent 智能层（Python）负责自主规划与工具调用，AIGC 编排层（Java）负责服务编排，传统后端层（Java）负责高并发调度与存储，算法层（Python）提供微调与实验**；Agent 层与 Java 后端通过 HTTP/MCP 通信，Redis Streams/RocketMQ 作为 Java↔Python Worker 总线，二进制走 MinIO，元数据走 MySQL，易变状态走 Redis。

```
┌─────────────────────────────────────────────────────────────────────┐
│              前端 SPA (Vue3 + TS + Vite + Pinia)                     │
│  3D查看器 │ 特效编辑器 │ 时间轴 │ Agent对话/轨迹面板 │ 项目/任务监控   │
└───────┬───────────────────────────────────┬───────────────┬─────────┘
        │ HTTP/REST (经 Gateway)              │ WebSocket(STOMP)│ SSE(Agent轨迹)
┌───────▼───────────────┐  ┌─────────────────▼───────────────▼─────────┐
│ Spring Cloud Gateway  │  │  ④ AI Agent 智能层 (Python 独立服务)        │
│ 路由/限流/JWT鉴权     │  │  FastAPI + LangChain/LangGraph + MCP SDK   │
│ /api/** → 后端        │  │  AI导演Agent: ReAct规划→工具调用→观察→反思  │
│ /agent/** → Agent服务 │  │  Agent记忆(短期/长期/反思)                  │
└───────┬───────────────┘  │  MCP Server: 暴露平台能力为MCP工具          │
        │                    └───────────────┬───────────────────────────┘
┌───────▼───────────────────────────────────▲───────────────────────────┐
│  ③ AIGC 服务编排层 + ② 传统后端编排层 (Java Spring Boot)               │
│  Auth/Project/Recon/Effect/Render/AgentRun/Finetune Service            │
│  TaskDispatcher(Streams+MQ生产者+状态机) │ ComfyUiClient(HTTP+WS)     │
│  GpuSlotManager(秒杀式抢槽) │ 语义缓存 │ 限流 │ MyBatis-Plus→MySQL     │
│  ShardingSphere分库分表 │ 读写分离 │ MinioClient                        │
│  AgentController: 接收Agent HTTP调用, 返回任务结果                      │
└───────────────┬───────────────────────────┬───────────────────────────┘
        投递     │ stream:recon/effect/render + RocketMQ │ 状态JSON回写
┌───────────────▼───────────────────────────▼───────────────────────────┐
│  ① Python GPU 执行层 + 算法层 (FastAPI + Streams/MQ 消费者)             │
│ 3DGS重建Worker │ ComfyUI特效Worker │ 微调训练Worker(diffusers/kohya)     │
│ ComfyUI常驻进程 │ 时序一致性工程模块 │ 实验运行器                          │
└───────────────┬───────────────────────────┬───────────────────────────┘
        ▼                ▼                              ▼
   MySQL(分片元数据)  Redis(Streams+缓存+Agent记忆)  MinIO(3D资产/图/视频)
```

**Agent ↔ 后端通信**：Agent（Python）通过 HTTP REST 调用 Java 后端的 `/api/internal/agent/*` 接口触发重建/特效/渲染任务，后端返回 `taskId` 后 Agent 轮询或订阅 STOMP 获取结果；同时 Agent 通过 MCP Server（Python FastAPI）将平台能力暴露为 MCP 工具，支持外部 MCP 客户端（如 Claude Desktop）接入。

**三条核心数据流**：
- **链路 A 重建**：Agent 或用户触发 → 存 MinIO+写 asset → 建 reconstruction_task → 投 `stream:recon` → Worker 消费（COLMAP→train→.splat→存 MinIO→回写+XACK）→ STOMP 推前端加载漫游。
- **链路 B 特效**：选视角截屏 → 配置/Agent 决策参数 → 建 effect_task → 投 `stream:effect` → ComfyUI Worker（渲染 workflow→/upload→/prompt→WS→/history→/view→存 MinIO→回写）。
- **链路 C Agent 自主创作**：自然语言目标 → Agent ReAct 规划 → 经 MCP 调用"重建/特效/渲染"工具 → 观察结果 → 反思重规划 → 剪辑合成。Agent 轨迹全程可视化。

---

## 五、模块划分与文件结构（Proposed Changes）

### 5.1 Java 后端 `e:\Excellent\mirage-backend\`（Maven 多模块）

| 模块 | 职责 | 对应层 |
|---|---|---|
| `mirage-gateway` | Spring Cloud Gateway：路由转发、JWT 鉴权、令牌桶限流、跨域 | 传统后端 |
| `mirage-common` | DTO、`R<T>`、异常枚举、常量、Redis key 规范、`TaskStatus` 状态机 | 通用 |
| `mirage-dao` | MyBatis-Plus Entity/Mapper、`MetaObjectHandler`、ShardingSphere 分片配置 | 传统后端 |
| `mirage-service` | Project/Asset/Recon/Effect/Render/User/WorkflowTemplate/AgentRun/Finetune Service | ②③ |
| `mirage-task` | `TaskDispatcher`(Streams+RocketMQ 生产者)、`TaskStatusStore`、`StreamWatchdog`(PEL 重分配)、`GpuSlotManager`(秒杀抢槽)、`WebSocketPusher`(STOMP) | 传统后端 |
| `mirage-integration` | `ComfyUiClient`(HTTP+WS)、`MinioClient`、`ComfyWorkflowRenderer`、`MqProducer` | ②③ |
| `mirage-web` | `@RestController`（含 `AgentInternalController` 供 Python Agent 调用）、`WebSocketConfig`、`SecurityConfig`(JWT)、`WebConfig`、全局异常 | Web |

> v3 变更：`mirage-agent` 模块从 Java 后端移除，Agent 逻辑改为 Python 独立服务（见 5.2）。Java 后端新增 `mirage-gateway` 网关模块与 `AgentInternalController`（供 Agent HTTP 调用）。

### 5.2 Python AI Agent 服务 `e:\Excellent\mirage-agent\`（v3 新增独立服务）

```
mirage-agent/
├── app.py                  # FastAPI 入口
├── agent/
│   ├── director_agent.py   # AI导演Agent: LangGraph ReAct 循环
│   ├── tools.py            # 工具定义: reconstruct/apply_effect/render_video/list_assets/edit_clip
│   ├── memory.py           # Agent记忆: 短期(当前规划) + 长期(创作经验, Redis+向量检索) + 反思
│   └── prompts.py          # ReAct prompt 模板
├── mcp_server/
│   ├── server.py           # MCP Python SDK Server: 暴露平台能力为 MCP 工具
│   └── tool_handlers.py    # MCP 工具实现: 调用 Java 后端 HTTP API
├── backend_client.py       # HTTP client: 调用 Java 后端 /api/internal/agent/*
├── stomp_client.py         # STOMP 订阅: 接收任务完成通知
├── requirements.txt        # langchain, langgraph, mcp, fastapi, httpx, stomp.py
└── Dockerfile
```

> 技术选型依据：AI Agent 开发岗以 Python 为绝对主力，LangChain/LangGraph/MCP Python SDK 为 JD 高频要求 [$TRAE_REF](https://m.zhipin.com/job_detail/fbf249d3f198668e03xz2du7GVBT.html)[$TRAE_REF](https://m.zhipin.com/job_detail/86da55d216b1257003153tS8GFFU.html)。

### 5.3 Python GPU 微服务 `e:\Excellent\mirage-gpu\`

```
mirage-gpu/
├── common/                 # stream_consumer.py、gpu_slot.py(Lua原子)、status_writer.py、minio_client.py
├── recon_worker/           # colmap_runner.py、train_runner.py、splat_exporter.py
├── effect_worker/          # workflow_renderer.py(Jinja2)、comfy_client.py、templates/*.json
├── finetune_worker/        # train_lora.py(diffusers/kohya)、train_controlnet.py、数据集处理
├── algorithm/              # temporal_consistency.py(3DGS视角时序一致性工程模块) + baselines
├── experiments/            # 实验运行器:config.yaml+run.py+metrics.json(消融)
└── requirements.txt
```

### 5.4 前端 `e:\Excellent\mirage-frontend\`（Vue3 + TS）

| 页面 | 路由 | 核心职责 |
|---|---|---|
| 项目工作台 | `/projects` | 项目列表/新建 |
| 重建向导 | `/projects/:id/reconstruct` | 照片分片上传、参数、进度 |
| **3D 查看器** | `/projects/:id/studio` | `SplatViewer.vue`(封装 GaussianSplats3D，走 `SplatRendererAdapter`)、OrbitControls、截屏 |
| **特效编辑器** | studio 右侧 | 模板/Prompt/ControlNet/AnimateDiff/Seed/结果对比 |
| **Agent 对话/轨迹面板** | studio 左侧 | 自然语言下达目标、ReAct 步骤流可视化、工具调用与观察、反思 |
| **时间轴** | studio 底部 | 相机+特效关键帧插值、渲染队列 |
| 任务/训练监控 | `/tasks` | 重建/特效/微调任务状态与日志 |

设计遵循 frontend-skill：Linear 风格克制、主工作区(3D)+Agent 面板+参数检查器+单一强调色(靛蓝 `#5B5BD6`)、utility 文案、2-3 处有意动效。

---

## 六、数据库与 Redis 设计

### 6.1 MySQL 表（`mirage-dao/.../schema.sql`）

原则：元数据落 MySQL，二进制落 MinIO，易变状态落 Redis；表带雪花 id、`created_at/updated_at`、逻辑删除。`asset`/各 task 表按 `project_id` 分片（ShardingSphere）。

1. `user`：id, username, password_hash, email, role, status, created_at
2. `project`：id, user_id, name, description, cover_asset_id, status, created_at, updated_at
3. `asset`：id, project_id, type(PHOTO/SPLAT/EFFECT_IMAGE/EFFECT_VIDEO/CAMERA_PATH/SCENE_SNAPSHOT/LORA_MODEL/CN_MODEL), storage_bucket, storage_key, size_bytes, mime, width, height, meta_json, created_at
4. `reconstruction_task`：id, project_id, source_asset_ids(json), status, params_json, splat_asset_id, error_msg, progress, started_at, finished_at
5. `effect_task`：id, project_id, source_snapshot_asset_id, template_id, params_json, status, result_asset_id, comfyui_prompt_id, error_msg, progress, started_at, finished_at
6. `render_job`：id, project_id, camera_path_asset_id, frame_count, fps, resolution, effect_template_id, effect_params_json, status, output_video_asset_id, started_at, finished_at
7. `workflow_template`：id, name, category, template_json, param_schema_json, thumbnail_url, enabled
8. **`agent_run`**（新增）：id, project_id, user_id, goal_text, plan_json(ReAct步骤), status, result_summary, total_tokens, total_steps, started_at, finished_at
9. **`agent_step`**（新增）：id, agent_run_id, step_index, thought, action(tool_name), action_input(json), observation, status, created_at
10. **`finetune_job`**（新增）：id, project_id, model_type(LORA/CONTROLNET), base_model, dataset_asset_ids(json), params_json(rank,lr,steps), status, output_model_asset_id, metrics_json(loss), started_at, finished_at
11. `task_log`：id, task_type, task_id, level, message, created_at

索引：`asset(project_id,type)`、各 task `(project_id,status)`、`agent_step(agent_run_id,step_index)`、`task_log(task_type,task_id,created_at)`。读写分离：主写从读，报表/列表走从库。

### 6.2 Redis 数据模型

| Key 模式 | 类型 | 用途 | TTL/备注 |
|---|---|---|---|
| `stream:recon` / `stream:effect` / `stream:render` / `stream:finetune` | Stream | 四类任务队列 | 消费组 `cg:*:gpuN`；MAXLEN ~10000 |
| `task:status:{taskId}` | String(JSON) | 任务实时状态 | TTL 24h |
| `gpu:slots:available` / `gpu:card:{id}:slots` | String(int) | GPU 槽位（秒杀式抢槽） | Lua 原子 decr/incr |
| `gpu:worker:lock:{workerId}` | String | Worker 心跳锁 | TTL 30s |
| `rate:bucket:{userId}` | String/Hash | 令牌桶限流 | 按用户 |
| `cache:semantic:{pHash}:{paramsHash}` | String(JSON) | ComfyUI 语义缓存 | TTL 7d |
| **`agent:memory:short:{runId}`** | String(JSON) | Agent 短期规划/当前步 | TTL 2h |
| **`agent:memory:long:{userId}`** | List/Stream | Agent 长期创作经验 | 持久（裁剪） |
| **`agent:reflection:{runId}`** | List | 反思日志 | TTL 7d |
| **`mcp:tool:cache`** | Hash | MCP 工具元数据缓存 | TTL 1h |
| `session:{token}` | String(JSON) | JWT 会话 | 与 token 一致 |
| `lock:task:{taskId}` | String | 幂等锁 | SETNX+TTL |
| `pq:effect` | ZSet | 优先级队列 | score=priority |

---

## 七、关键技术集成方案

### 7.1 COLMAP 调度
`ReconService` 校验照片数(5–200)后投 `stream:recon`；Worker 优先 `colmap automatic_reconstructor`，高级模式拆 `feature_extractor+exhaustive_matcher+mapper+image_undistorter`；失败回写 `COLMAP_INSUFFICIENT_MATCHES`；`train.py` 解析 `iteration X/30000` 回写 progress。可调参数对应实验。

### 7.2 ComfyUI 工作流编排
Web UI 搭图→"Save (API Format)"导出 JSON 存 `workflow_template.template_json`，占位符 `{{prompt}}/{{seed}}/{{input_image}}/{{cn_strength}}`；`param_schema_json` 驱动前端动态表单。时序：`/upload/image`→渲染填 LoadImage→`POST /prompt`(client_id)→WS 收 `progress/executing(node=null)/execution_error`→`/history`→`/view`→存 MinIO。WS 断线指数退避(1s→60s)；超时 `/interrupt`。四类模板：风格化(ControlNet+LoRA)/元素增删(SAM+Inpainting)/电影调色(IPAdapter)/视频特效(AnimateDiff)。

### 7.3 3D 资产存储分发
MinIO 分桶；Nginx Range + GaussianSplats3D `progressiveLoad` 流式加载；推荐 `.ksplat`；Spring 签 15min presigned URL。

### 7.4 GPU 任务调度与网关（传统后端核心点）
- **Spring Cloud Gateway**：作为统一入口，路由 `/api/**`→Java 后端、`/agent/**`→Python Agent 服务；Gateway 层做 JWT 鉴权、令牌桶限流（`RequestRateLimiter` filter + Redis Lua）、跨域处理——直接对应传统后端面试的网关/限流考察点 [$TRAE_REF](https://blog.csdn.net/2402_84764726/article/details/156807998)。
- **秒杀式高并发抢槽**：多用户并发提交时，GPU 槽位为稀缺资源，用 Lua 原子 `DECR gpu:slots:available`（≤0 阻塞 BLPOP 等待），完成 `INCR` 唤醒——这是经典秒杀/库存扣减场景，直接对应传统后端高并发考察点。
- 多卡按 `gpu:card:{id}:slots` 细分 + `CUDA_VISIBLE_DEVICES` 绑定。
- **双消息队列对比**：Redis Streams 做任务可靠投递（PEL/XCLAIM），RocketMQ 做跨服务事件流与最终一致性（展示 MQ 深度，面试常问 Streams vs MQ 取舍）。
- `StreamWatchdog`(Spring 定时)扫超时未 ACK `XCLAIM` 重分配；ZSet 优先级队列；Prometheus+Grafana 监控。
- **分库分表**：`asset`/task 表按 `project_id` 分片（ShardingSphere），单表数据量大时水平扩展；MySQL 索引调优 + explain + 读写分离。
- **微服务设计讨论**：面试中可讨论拆分为 `mirage-gateway`/`mirage-backend`/`mirage-agent` 三服务的微服务架构（Nacos 注册中心、Feign 调用、Sentinel 熔断），MVP 阶段以 Gateway + 模块化单体先行，后续按需拆分。

### 7.5 AI Agent 层（AI Agent 岗核心，Python 实现）
- **AI 导演 Agent**：基于 **Python LangChain/LangGraph** 实现 ReAct 循环——`Thought→Action(调用工具)→Observation→反思→下一步`。给定自然语言目标，自主规划"重建→选视角→特效→渲染→剪辑"全流程，失败时反思重规划。LangGraph 的 `StateGraph` 管理 Agent 状态机，支持条件分支与回退。
- **MCP Server**：用 **MCP Python SDK** 把平台能力封装为 MCP 工具（`reconstruct`/`apply_effect`/`render_video`/`list_assets`/`edit_clip`），通过 FastAPI 暴露 MCP 协议端点（用 mcp-builder 技能指导）。这使平台本身可被任意 MCP 客户端（Claude Desktop 等）复用。
- **Agent ↔ 后端通信**：Agent 通过 `httpx` 调用 Java 后端 `AgentInternalController` 的 `/api/internal/agent/*` 接口触发任务，后端返回 `taskId`；Agent 通过 STOMP 订阅或轮询获取结果。Agent 轨迹（`agent_run`/`agent_step`）由 Agent 服务写回 Java 后端落库。
- **Agent 记忆**：短期（当前规划/步，Redis `agent:memory:short:{runId}`）、长期（创作经验，Redis List + 向量化检索 `agent:memory:long:{userId}`）、反思日志（`agent:reflection:{runId}`）。多轮创作跨 run 复用经验。
- **可观测**：`agent_run`/`agent_step` 表落库 + 前端通过 SSE 接收 Agent 轨迹流可视化（Thought/Action/Observation 逐步展示）。
- **工具调用闭环**：Agent 调工具 → HTTP 调 Java 后端 → 后端投 Stream → Python GPU Worker 执行 → 结果回写 → Agent 观察并决策下一步。

### 7.6 AIGC 算法层（AIGC 算法/算法岗核心）
- **LoRA 微调**：用 diffusers/kohya 训练自定义风格 LoRA（自建小数据集，rank/lr/steps 可配），产出 `LORA_MODEL` 资产，挂载进 ComfyUI 风格化模板——展示训练/微调能力。JD 调研确认 AIGC 算法岗**以微调为主流要求**，不要求从零预训练 [$TRAE_REF](https://m.zhipin.com/job_detail/94bdfdcc3ef81a7103d42du9E1tR.html)。
- **ControlNet 微调**：针对 3D 视角条件训练/微调一个 ControlNet，用于视角一致性控制——展示可控生成能力。
- **时序一致性工程模块 `temporal_consistency.py`**：针对多视角特效帧间闪烁问题，实现基于 3DGS 几何先验的跨帧特征 warp + 一致性约束方法。**诚实定位**：这是"工程平台上的算法实验模块"，通过消融实验量化效果，而非论文级学术创新。投递算法岗时以"代码能力+工程化实验+系统化评测"为主线。
- **评测**：PSNR/SSIM/LPIPS（重建）、FID/CLIP Score（特效）、warp-LPIPS/抖动方差（时序一致性）；消融对比 baselines（逐帧处理/AnimateDiff/ControlNet）。

### 7.7 模型来源与推理部署决策
特效侧以开源模型(SD1.5/SDXL/AnimateDiff/ControlNet)+ 自训 LoRA/ControlNet 为主，避免付费 API；重建侧开源 gaussian-splatting+COLMAP；Agent 侧可用开源 LLM（Qwen/DeepSeek）本地或 API。

**推理部署讨论（AI 后端加分项）**：MVP 阶段以 ComfyUI 作为推理后端（支持灵活 workflow 编排）；技术报告中对比讨论 vLLM（LLM 高吞吐推理）、Triton Inference Server（多框架统一部署）、TensorRT（模型加速）的生产部署方案，展示对推理优化生态的理解 [$TRAE_REF](https://m.yupao.com/zhaogong/384320098.html)。

---

## 八、分阶段实施计划

### 阶段一：MVP（端到端最小闭环 + 基础后端）
上传照片→重建→浏览器漫游→对某视角应用一种风格化特效→保存。
- Java 后端：多模块骨架（含 `mirage-gateway`）；user/project/asset/reconstruction_task/effect_task 表+CRUD；JWT；`TaskDispatcher`+`TaskStatusStore` 最小版；STOMP。
- Python GPU：`recon_worker`(COLMAP+train.py 降 ~7000+.ply→.splat)；`effect_worker`(单风格化模板全链路)；单卡单槽。
- 前端：工作台、重建向导、3D 查看器、特效编辑器(单模板)、任务监控。
- 基础设施：Docker Compose(MySQL+Redis+MinIO+ComfyUI+Gateway)+Nginx。

### 阶段二：Agent 智能层（→ AI Agent 岗，Python 服务）
- `mirage-agent`（Python 独立服务）：`DirectorAgent` LangGraph ReAct 循环 + 工具调用；MCP Python Server 暴露平台能力（mcp-builder 技能）；Agent 记忆（Redis）；`agent_run`/`agent_step` 表（Java 后端 `AgentInternalController`）。
- Java 后端：新增 `AgentInternalController` 供 Agent HTTP 调用；Agent 轨迹落库。
- 前端：Agent 对话/轨迹面板（ReAct 步骤流可视化，SSE 接收）。
- 多模板特效 + 动态参数检查器；元素增删/电影调色模板。
- 视频导出：时间轴关键帧→拆帧→ComfyUI→ffmpeg 合成。

### 阶段三：算法层（→ AIGC 算法/算法岗）
- `finetune_worker`：风格 LoRA 训练 + 3D 视角 ControlNet 微调；`finetune_job` 表。
- `algorithm/temporal_consistency.py`：时序一致性工程模块，接入视频特效流水线。
- 8 组消融实验（见第九节），产出可复现代码 + 技术报告。

### 阶段四：传统后端加固（→ 传统后端岗）
- GPU 秒杀式抢槽压测 + Gateway 限流加固；RocketMQ 事件流与 Streams 对比。
- ShardingSphere 分库分表 + 读写分离；MySQL 索引调优/explain。
- 语义缓存、GPU 多槽/多卡、`StreamWatchdog`、优先级队列、Prometheus 可观测、前端任务日志面板。
- 可选：JVM 线程池调优、CompletableFuture 编排、分布式事务、Nacos/Sentinel 微服务化。

---

## 九、工程难点与对应科研实验点

| # | 工程难点 | 对应岗位/层 | 实验设计 | 期望产出 |
|---|---|---|---|---|
| 1 | 3DGS 重建慢 | 算法 | 迭代数{7k,15k,30k}×照片数{5,15,50};PSNR/SSIM/LPIPS+耗时 | 帕累托曲线 |
| 2 | 视频特效帧间闪烁 | **算法** | baselines(逐帧/AnimateDiff/ControlNet) **vs 时序一致性工程模块**;warp-LPIPS/抖动/MOS | 量化时序一致性模块效果，闪烁降低率（工程实验，非学术声明） |
| 3 | GPU 资源争用 | 传统后端 | FIFO/优先级/SJF/资源感知;吞吐/P99/利用率 | 资源感知策略吞吐提升 X% |
| 4 | ComfyUI 缓存命中 | AI 后端 | 精确哈希/pHash 阈值/参数模糊;命中率/延迟/质量损失 | pHash 权衡曲线 |
| 5 | .splat 渲染性能 | 前端/算法 | GaussianSplats3D(.ply/.splat/.ksplat) vs Spark;加载/FPS/伪影 | 格式选型+Spark 迁移可行性 |
| 6 | COLMAP 稀疏视角失败 | 算法 | 照片数{3,5,8,15}+CF-3DGS 兜底;成功率/PSNR | 稀疏兜底策略 |
| 7 | **LoRA/ControlNet 微调效果** | **AIGC 算法** | rank{8,16,32}×steps;FID/CLIP/风格相似度;对比 base 模型 | 微调超参曲线，证明自训模型优于 base |
| 8 | **Agent 规划成功率** | **AI Agent** | ReAct vs 直接调用 vs 多智能体;任务完成率/步数/token/重规划率 | Agent 架构对比，规划策略选型 |

每实验配 `experiments/<name>/config.yaml`+`run.py`，固定种子与模型版本，输出 `metrics.json`+自动绘图，确保可复现。

---

## 十、假设与决策（Assumptions & Decisions）

1. **GPU 资源**：有云 GPU(AutoDL/Colab)或本地 N 卡(CUDA≥7.x)。无 GPU 时后端/前端/Agent 逻辑可先用 mock Worker 开发，微调与重建需 GPU。
2. **架构决策**：四层架构，Java 后端编排+Python GPU 执行+Python Agent 智能层+Python 算法层，Redis Streams/RocketMQ 总线——一层对应一类岗位，**每层使用该岗位主流语言**。
3. **渲染库**：MVP 用 GaussianSplats3D，`SplatRendererAdapter` 接口隔离，预留 Spark 迁移。
4. **Agent 实现（v3 修正）**：Agent 层用 **Python**（LangChain/LangGraph + MCP Python SDK）而非 Java（LangChain4j），因为 AI Agent JD 以 Python 为绝对主力 [$TRAE_REF](https://m.zhipin.com/job_detail/fbf249d3f198668e03xz2du7GVBT.html)。Agent 作为独立 FastAPI 服务，与 Java 后端通过 HTTP/MCP 通信。
5. **算法定位（v3 修正）**：时序一致性模块定位为"工程实验模块"而非"学术创新贡献"，通过消融实验量化效果。投递算法岗以代码能力+工程化实验为主线，不包装为论文级贡献。微调展示工程化训练能力。
6. **范围**：不做社交/支付/多租户计费（YAGNI），聚焦四层深度。项目体量大，按四阶段递进，每阶段独立可演示。
7. **网关与微服务**：MVP 用 Spring Cloud Gateway 做统一入口（路由/限流/鉴权）；微服务全套（Nacos/Feign/Sentinel）在面试中讨论设计，按需逐步落地。
8. **技能运用规划**：redis-development（Redis 建模/性能）、frontend-skill（工作室 UI）、mcp-builder（MCP Python Server）、canvas-design/figma（UI 视觉）、gh-cli（仓库/CI）、writing-plans（任务级实现计划）；brainstorming 已用于本次设计。

---

## 十一、验证方案

### 11.1 测试金字塔
- **单元**：JUnit5+Mockito 测 Service、`ComfyWorkflowRenderer` 占位符断言、`GpuSlotManager` 原子性(Embedded Redis)；pytest 测 `director_agent` 工具选择逻辑(LangGraph 状态断言)、`workflow_renderer/splat_exporter/temporal_consistency/train_lora`(mock)；Vitest 测 Pinia store 与 `SplatRendererAdapter` 契约。
- **集成**：Testcontainers(MySQL/Redis/MinIO)跑"建项目→上传→投任务→模拟 Worker→查结果"全链路；`StreamWatchdog` 重分配；Agent(Python)→HTTP→Java 后端→Stream→Worker 全链路；MCP 工具调用端到端。
- **E2E**：Playwright 跑"登录→上传→等重建→加载 3D→应用特效→看结果"与"Agent 对话→自主创作→看轨迹"(mock GPU)。
- **契约**：Java↔Python Stream 消息体与 `task:status` JSON 用 JSON Schema 双向校验；Agent↔后端 HTTP 接口用 OpenAPI 校验；MCP 工具 schema 校验。

### 11.2 性能与压测
- API：k6/JMeter 压"提交特效任务"验证限流与背压；**GPU 抢槽秒杀压测**（高并发提交验证 Lua 原子性与超卖防护）。
- GPU：重建/特效/微调 P50/P95/P99 耗时与显存峰值、并发槽位调度吞吐。
- Agent：任务完成率/平均步数/token 成本/重规划率。
- 前端：Lighthouse 测 3D 首屏与 FPS、大 splat(800 万点)加载。

### 11.3 质量评估（科研维度）
- 重建：留出视角 `render.py` 算 PSNR/SSIM/LPIPS 对比官方基线。
- 特效：FID/CLIP Score/MOS(A/B 盲测)。
- 时序：相邻帧光流 warp 后 LPIPS、抖动方差。
- **微调**：自训 LoRA/ControlNet vs base 模型 FID/CLIP/风格相似度。
- **Agent**：规划成功率、步数、token、重规划率（实验 8）。
- 可复现：Docker 固定版本(CUDA/torch/ComfyUI commit/gaussian-splatting commit/diffusers commit)；`experiments/` 留 config+seed+metrics；README 一键复现。

### 11.4 工程健壮性
Worker 被 kill→`StreamWatchdog` `XCLAIM` 重分配；ComfyUI 崩溃/WS 断→重连+`/interrupt`；MinIO 断网→重试+孤儿清理；大文件→Range+`progressiveLoad`；Agent 工具调用失败→反思重规划。

---

## 十二、风险与应对

| 风险 | 应对 |
|---|---|
| GaussianSplats3D 已停更 | `SplatRendererAdapter` 接口隔离，预留 Spark 迁移 |
| GPU 资源不足 | mock Worker 先开发；重建迭代降 ~7000；云 GPU 按需启停 |
| COLMAP 稀疏视角失败 | 实验点 6 兜底(CF-3DGS) |
| 视频特效帧间闪烁 | 时序一致性工程模块(实验点 2)，诚实评估效果 |
| ComfyUI 工作流复杂易错 | 模板化+`param_schema_json`+JSON Schema 契约+`node_errors` 落日志 |
| 项目体量大 | 四阶段递进，每阶段独立可演示；按目标岗位裁剪强调层；优先完成阶段一+二 |
| Agent 规划不稳定 | ReAct+反思+工具 schema 约束；实验点 8 对比架构选最优 |
| 微调数据/算力不足 | 小数据集 LoRA(rank 低)+迁移学习；云 GPU 短时训练 |
| Java↔Python Agent 跨服务调试 | 统一 JSON Schema 契约；OpenAPI 文档；docker-compose 本地一键起全链路 |

---

## 十三、交付物清单
- `e:\Excellent\mirage-backend\`：Java Spring Boot 多模块后端（含 `mirage-gateway` 网关 + `AgentInternalController`）
- `e:\Excellent\mirage-agent\`：Python AI Agent 服务（LangChain/LangGraph ReAct + MCP Python Server）
- `e:\Excellent\mirage-gpu\`：Python GPU 微服务（重建+特效+微调+时序一致性模块+实验）
- `e:\Excellent\mirage-frontend\`：Vue3+TS 工作室前端（3D+特效+Agent 面板）
- `docker-compose.yml`：MySQL+Redis+MinIO+RocketMQ+ComfyUI+Gateway+Agent+Nginx 一键起
- GitHub 仓库（README+技术博客+可复现实验脚本+技术报告）：gh-cli 管理与 CI
