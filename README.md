# Nexus AI - 企业级垂直领域 RAG 知识检索平台

> **专注于垂直领域的高精度知识库问答解决方案**

Nexus AI 是一个基于 **Spring Boot 3** 和 **LangChain4j** 构建的现代化 RAG（检索增强生成）系统。它集成了全栈基础设施（Docker Compose）、企业级鉴权（Spring Security + JWT）、可观测性（Micrometer + Zipkin + Grafana）和高性能缓存架构（Caffeine + Redis），旨在解决通用大模型在专业领域中的知识幻觉问题。

---

## 🌟 核心特性 (Features)

### 🧠 智能检索增强 (RAG Core)
*   **垂直领域专注**: 针对 PDF/Docx/Txt 优化的文档切片策略，支持专业术语精准召回。
*   **混合模型支持**: 支持普通对话模型 (GPT-4o/DeepSeek-V3) 与 推理模型 (Reasoning/O1) 无缝切换。
*   **全流式响应**: 基于 SSE (Server-Sent Events) 的打字机效果，极大降低首字延迟。

### 🚀 高性能架构
*   **多级缓存 (Multi-level Caching)**:
    *   **L1 本地缓存**: Caffeine (JVM 堆内)，毫秒级响应高频热点问题。
    *   **L2 语义缓存**: Redis Stack (Vector Similarity)，基于语义相似度命中历史问答，降低 LLM Token 消耗。
*   **高并发限流**: 集成 Resilience4j，保护后端 API 免受流量洪峰冲击。

### 🛡️ 企业级安全
*   **认证与鉴权**: 完整的 JWT 登录/注册流程，集成 Spring Security。
*   **数据隔离**: 用户级数据隔离设计，确保“谁上传的文档谁能问”。

### 📊 可观测性 (Observability)
*   **全链路追踪**: 集成 Zipkin，可视化请求从 Controller -> Redis -> Milvus -> LLM 的全链路耗时。
*   **监控大屏**: 集成 Prometheus + Grafana，实时监控 JVM、HTTP 请求、线程池等核心指标。

---

## 🛠️ 技术栈 (Tech Stack)

### 后端 (Backend)
*   **框架**: Java 17, Spring Boot 3.3.5
*   **AI 编排**: LangChain4j 0.35.0
*   **数据库**: MySQL 8.0 (MyBatis-Plus)
*   **向量库**: Milvus 2.3.0 (Standalone)
*   **缓存**: Redis Stack (Vector Search) + Caffeine
*   **对象存储**: MinIO
*   **消息队列**: Kafka + Zookeeper (异步任务处理)

### 前端 (Frontend)
*   **框架**: Python Streamlit (快速交互原型)
*   **鉴权**: Session State + JWT API Integration

### 基础设施 (Infrastructure)
*   **容器化**: Docker, Docker Compose
*   **监控**: Prometheus, Grafana, Zipkin, Attu (Milvus GUI)

---

## 📂 项目结构

```bash
nexus-ai
├── src/                 # Java 后端源码
├── fronted/             # Python Streamlit 前端
├── docker-compose.yml   # 全栈基础设施编排
├── prometheus.yml       # 监控配置
├── docs/                # 项目文档归档
└── volumes/             # [自动生成] 数据库持久化数据 (MySQL, MinIO, Milvus...)
```

---

## 🚦 快速开始 (Quick Start)

### 1. 启动基础设施
本项目依赖多个中间件，建议使用 Docker Compose 一键拉起：

```bash
# 启动所有服务 (MySQL, Redis, MinIO, Milvus, Kafka, Zipkin, Grafana...)
docker-compose up -d
```

**服务端口清单**:
*   MySQL: `localhost:3307` (注意不是 3306)
*   Redis: `localhost:6379`
*   MinIO: `localhost:9000` (API) / `9001` (Console)
*   Milvus: `localhost:19530` / Attu GUI: `http://localhost:8000`
*   Zipkin: `http://localhost:9411`
*   Grafana: `http://localhost:3000` (User/Pass: admin/admin)

### 2. 启动后端 (Backend)
确保 JDK 17 已安装。

```bash
# 编译并运行
mvn spring-boot:run
```
后端服务地址: `http://localhost:8080`

### 3. 启动前端 (Frontend)
确保 Python 3.8+ 已安装。

```bash
cd fronted
pip install -r requirements.txt
streamlit run app.py
```
访问地址: `http://localhost:8501`

---

## 🔐 默认账号

*   **MinIO**: `minioadmin` / `minioadmin`
*   **MySQL**: `root` / `root`
*   **Grafana**: `admin` / `admin`
*   **应用登录**: 自助注册新账号即可使用。

---

## 📄 License
MIT License
