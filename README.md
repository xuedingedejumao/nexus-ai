# Nexus AI - 垂直领域 RAG 知识检索平台

Nexus AI 是一个基于 **Spring Boot 3** 和 **LangChain4j** 构建的 RAG（检索增强生成）系统。它集成了完整的容器化基础设施、JWT 鉴权机制、全链路可观测性以及多级缓存架构，旨在解决通用大模型在专业领域中的知识幻觉问题。

---

## 核心特性

### 智能检索增强
*   **混合检索**: BM25 倒排索引 + kNN 向量检索双路召回，通过 RRF（Reciprocal Rank Fusion）融合排序，兼顾精确关键词匹配与语义相似度。
*   **二次精排**: 基于 Embedding 余弦相似度的 Rerank 机制，从粗排候选中精选高相关性上下文。
*   **垂直领域优化**: 针对 PDF/Docx/Txt 格式文档优化的切片策略，提升专业术语召回率。
*   **多模型支持**: 支持普通对话模型（DeepSeek-V3）与深度推理模型（DeepSeek-R1）切换。
*   **流式响应**: 基于 SSE（Server-Sent Events）实现打字机效果的逐 Token 输出。

### 架构设计
*   **多级缓存**:
    *   **L1 本地缓存**: Caffeine（JVM 堆内），拦截高频热点查询。
    *   **L2 语义缓存**: Redis Stack（Vector Similarity Search），基于语义相似度匹配历史问答，有效命中"同义不同词"的重复查询。
*   **异步文档处理**: 文件上传后通过 Kafka 解耦，由消费者异步完成文本提取（Apache Tika）与向量化入库。
*   **服务治理**: 集成 Resilience4j 实现 API 限流，保护下游大模型接口。
*   **入参校验**: 基于 Jakarta Validation 的统一入参校验，全局异常处理器统一返回格式。

### 安全与监控
*   **认证鉴权**: 基于 JWT + Spring Security 的无状态认证。
*   **会话隔离**: 基于用户维度的会话记忆隔离，确保多用户并发场景下上下文不串扰。
*   **可观测性**:
    *   **链路追踪**: Zipkin 全链路请求耗时可视化。
    *   **指标监控**: Prometheus + Grafana 系统核心指标看板。

---

## 技术栈

### 后端
*   **框架**: Java 17, Spring Boot 3.3.5
*   **AI 编排**: LangChain4j 0.35.0
*   **Embedding 模型**: BGE-small-zh-v1.5（ONNX 量化，本地推理）
*   **数据库**: MySQL 8.0（MyBatis-Plus）
*   **检索引擎**: Elasticsearch 8.x（BM25 + kNN 混合检索）
*   **缓存**: Redis Stack（向量语义缓存）+ Caffeine（本地热点缓存）
*   **对象存储**: MinIO
*   **消息队列**: Kafka + Zookeeper
*   **文本解析**: Apache Tika

### 前端
*   **框架**: Python Streamlit
*   **交互**: Session State 管理与 REST API 集成

### 基础设施
*   **部署**: Docker Compose 全栈编排
*   **监控**: Prometheus, Grafana, Zipkin

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

### 1. 环境变量配置

在启动前，请先配置大模型 API Key 环境变量：

```bash
# Linux / macOS
export DEEPSEEK_API_KEY=your_api_key_here

# Windows PowerShell
$env:DEEPSEEK_API_KEY="your_api_key_here"
```

### 2. 启动基础设施

使用 Docker Compose 一键启动所有依赖服务：

```bash
docker-compose up -d
```

**服务端口说明**:
*   MySQL: `localhost:3307`
*   Redis: `localhost:6379`
*   MinIO: `localhost:9000`（API）/ `9001`（Console）
*   Elasticsearch: `localhost:9200`
*   Kafka: `localhost:9092`
*   Zipkin: `http://localhost:9411`
*   Grafana: `http://localhost:3000`（admin / admin）

### 3. 启动后端

确保 JDK 17 已安装。

```bash
mvn spring-boot:run
```
后端服务地址: `http://localhost:8080`

### 4. 启动前端

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
