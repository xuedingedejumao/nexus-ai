# Nexus AI - 垂直领域 RAG 知识检索平台

Nexus AI 是一个基于 **Spring Boot 3** 和 **LangChain4j** 构建的 RAG（检索增强生成）系统。它集成了完整的容器化基础设施、JWT 鉴权机制、全链路可观测性以及多级缓存架构，旨在解决通用大模型在专业领域中的知识幻觉问题。

---

## 核心特性

### 智能检索增强
*   **垂直领域优化**: 针对 PDF/Docx/Txt 格式文档优化的切片策略，提升专业术语召回率。
*   **多模型支持**: 支持普通对话模型 (GPT-4o/DeepSeek-V3) 与 推理模型 (Reasoning/O1) 切换。
*   **流式响应**: 基于 SSE (Server-Sent Events) 实现打字机效果输出。

### 架构设计
*   **多级缓存**:
    *   **L1 本地缓存**: Caffeine (JVM 堆内)，加速高频热点查询。
    *   **L2 语义缓存**: Redis Stack (Vector Similarity)，基于语义相似度匹配历史问答。
*   **服务治理**: 集成 Resilience4j 实现 API 限流，保护后端服务。

### 安全与监控
*   **认证鉴权**: 基于 JWT + Spring Security 的登录与注册流程。
*   **数据隔离**: 用户级数据隔离，确保私有文档的安全性。
*   **可观测性**:
    *   **链路追踪**: 集成 Zipkin，可视化全链路请求耗时。
    *   **指标监控**: 集成 Prometheus + Grafana，监控系统核心指标。

---

## 技术栈

### 后端
*   **框架**: Java 17, Spring Boot 3.3.5
*   **AI 编排**: LangChain4j 0.35.0
*   **数据库**: MySQL 8.0 (MyBatis-Plus)
*   **向量库**: Milvus 2.3.0
*   **缓存**: Redis Stack + Caffeine
*   **对象存储**: MinIO
*   **消息队列**: Kafka + Zookeeper

### 前端
*   **框架**: Python Streamlit
*   **交互**: Session State 管理与 REST API 集成

### 基础设施
*   **部署**: Docker Compose 全栈编排
*   **监控**: Prometheus, Grafana, Zipkin, Attu

---

## 项目结构

```bash
nexus-ai
├── src/                 # Java 后端源码
├── fronted/             # Python Streamlit 前端
├── docker-compose.yml   # 基础设施编排配置
├── prometheus.yml       # 监控配置
├── docs/                # 文档归档
└── volumes/             # 数据库持久化数据
```

---

## 快速开始

### 1. 启动基础设施
建议使用 Docker Compose 一键启动所有依赖服务：

```bash
# 启动所有服务 (MySQL, Redis, MinIO, Milvus, Kafka, Zipkin, Grafana...)
docker-compose up -d
```

**服务端口说明**:
*   MySQL: `localhost:3307` (注意端口映射)
*   Redis: `localhost:6379`
*   MinIO: `localhost:9000` (API) / `9001` (Console)
*   Milvus: `localhost:19530` / Attu GUI: `http://localhost:8000`
*   Zipkin: `http://localhost:9411`
*   Grafana: `http://localhost:3000` (User/Pass: admin/admin)

### 2. 启动后端
确保 JDK 17 已安装。

```bash
mvn spring-boot:run
```
后端服务地址: `http://localhost:8080`

### 3. 启动前端
确保 Python 3.8+ 已安装。

```bash
cd fronted
pip install -r requirements.txt
streamlit run app.py
```
访问地址: `http://localhost:8501`

---

## 默认账号

*   **MinIO**: `minioadmin` / `minioadmin`
*   **MySQL**: `root` / `root`
*   **Grafana**: `admin` / `admin`
*   **应用登录**: 请在前端注册新账号使用。

---

## License
MIT License
