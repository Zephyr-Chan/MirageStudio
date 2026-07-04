# ============================================================
# MirageStudio GPU 微服务 Dockerfile
# 支持 recon_worker（3D 重建）和 effect_worker（ComfyUI 渲染）
# ============================================================

FROM python:3.11-slim

# 设置环境变量
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

# 安装系统依赖
# - libgl1 / libglib2.0-0: Pillow / OpenCV 运行时依赖
# - libgomp1: numpy / torch OpenMP 依赖
# - wget: 用于下载模型文件
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    libgomp1 \
    wget \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 创建工作目录
WORKDIR /app

# 先复制依赖文件，利用 Docker 层缓存
COPY requirements.txt /app/requirements.txt

# 安装 Python 依赖
RUN pip install -r requirements.txt

# 复制项目代码
COPY . /app/

# 创建数据目录
RUN mkdir -p /app/data /app/workspace

# ============================================================
# 默认配置（可通过环境变量覆盖）
# ============================================================
ENV REDIS_URL=redis://redis:6379/0 \
    MINIO_ENDPOINT=minio:9000 \
    MINIO_ACCESS_KEY=minioadmin \
    MINIO_SECRET_KEY=minioadmin \
    MINIO_BUCKET=mirage \
    COMFYUI_URL=http://comfyui:8188 \
    JAVA_BACKEND_URL=http://java-backend:8080 \
    GPU_ID=0 \
    GPU_SLOT_CAPACITY=1 \
    CONSUMER_GROUP=gpu-workers \
    MOCK_RECON=false \
    MOCK_EFFECT=false \
    LOG_LEVEL=INFO

# ============================================================
# 以下为可选的外部工具安装（取消注释以启用）
# ============================================================

# COLMAP 安装（用于 recon_worker 真实模式）
# RUN apt-get update && apt-get install -y --no-install-recommends colmap && rm -rf /var/lib/apt/lists/*

# Gaussian Splatting 安装（需要 NVIDIA GPU + CUDA）
# COPY gaussian-splatting /opt/gaussian-splatting
# RUN pip install -e /opt/gaussian-splatting
# ENV GS_TRAIN_SCRIPT=/opt/gaussian-splatting/train.py

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=10s \
    CMD python -c "import redis; redis.Redis.from_url('$REDIS_URL').ping()" || exit 1

# ============================================================
# 入口点：通过 WORKER_TYPE 环境变量选择工作器类型
#   WORKER_TYPE=recon  -> 运行 recon_worker
#   WORKER_TYPE=effect -> 运行 effect_worker
# ============================================================
ENV WORKER_TYPE=recon

CMD ["sh", "-c", "python -m ${WORKER_TYPE}_worker.main"]
