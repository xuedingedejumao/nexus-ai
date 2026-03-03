# Nexus AI - 企业级 RAG 知识检索平台

Nexus AI 是一个基于 **Spring Boot 3** 和 **LangChain4j** 构建的 RAG（检索增强生成）系统。它集成了 **Elasticsearch 混合检索**（BM25 + 向量语义检索 + RRF 融合排序）、JWT 鉴权、全链路可观测性以及多级缓存架构，旨在解决通用大模型在专业领域中的知识幻觉问题。

---

## 核心特性

### 智能混合检索
*   **BM25 关键词检索 + 向量语义检索**: 基于 Elasticsearch 实现双路召回，通过 RRF（Reciprocal Rank Fusion）融合排序，兼顾精确匹配与语义理解。
*   **本地向量化**: 集成 BGE-small-zh 中文向量模型（ONNX 本地推理），无需依赖外部 Embedding API。
*   **多格式文档支持**: 支持 PDF / Docx / Txt 格式文档上传与智能切片。

### AI 对话
*   **多模型切换**: 支持 DeepSeek-V3 普通对话模型与 DeepSeek-Reasoner 推理模型。
*   **流式响应**: 基于 SSE (Server-Sent Events) 实现打字机效果输出。

### 架构设计
*   **多级缓存**:
    *   **L1 本地缓存**: Caffeine (JVM 堆内)，加速高频热点查询。
    *   **L2 语义缓存**: Redis Stack (Vector Similarity)，基于语义相似度匹配历史问答。
*   **异步解耦**: Kafka 消息队列异步处理文档解析与向量化任务。
*   **服务治理**: 集成 Resilience4j 实现 API 限流保护。

### 安全与监控
*   **认证鉴权**: JWT + Spring Security，支持用户注册与登录。
*   **数据隔离**: 用户级文档与对话数据隔离。
*   **可观测性**:
    *   **链路追踪**: Zipkin 可视化全链路请求耗时。
    *   **指标监控**: Prometheus + Grafana 监控 JVM、API 延迟等核心指标。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| **语言/框架** | Java 17, Spring Boot 3.3.5 |
| **AI 编排** | LangChain4j 0.36.0 |
| **向量模型** | BGE-small-zh-v1.5 (ONNX 本地推理) |
| **搜索引擎** | Elasticsearch 8.12 (BM25 + KNN + RRF) |
| **数据库** | MySQL 8.0 (MyBatis-Plus) |
| **缓存** | Redis Stack + Caffeine |
| **对象存储** | MinIO |
| **消息队列** | Kafka + Zookeeper |
| **安全** | Spring Security + JWT (jjwt) |
| **监控** | Prometheus, Grafana, Zipkin |
| **部署** | Docker Compose |
| **前端** | Python Streamlit |

---

## 项目结构

```
nexus-ai/
├── src/                     # Java 后端源码
│   └── main/java/.../
│       ├── config/          # 配置类 (Security, Redis, MinIO, ES...)
│       ├── controller/      # REST 控制器
│       ├── service/         # 业务逻辑层
│       ├── filter/          # JWT 认证过滤器
│       ├── entity/          # 数据库实体
│       ├── mapper/          # MyBatis-Plus Mapper
│       ├── dto/             # 数据传输对象
│       └── utils/           # 工具类 (JWT, 文档解析等)
├── fronted/                 # Python Streamlit 前端
├── docker-compose.yml       # 基础设施编排
├── prometheus.yml           # Prometheus 监控配置
├── .env.example             # 环境变量模板
└── README.md
```

---

## 快速开始

### 前置条件
*   JDK 17+
*   Docker & Docker Compose
*   Python 3.8+ (前端)

### 1. 克隆项目

```bash
git clone https://github.com/your-username/nexus-ai.git
cd nexus-ai
```

### 2. 配置环境变量

```bash
# 复制环境变量模板
cp .env.example .env
```

编辑 `.env` 文件，填入你的真实配置：

```bash
# DeepSeek API Key (必填，获取地址: https://platform.deepseek.com/api_keys)
DEEPSEEK_API_KEY=your-deepseek-api-key-here

# JWT 签名密钥 (必填，建议使用随机长字符串)
JWT_SECRET_KEY=your-jwt-secret-key-here

# MinIO 密钥 (可选，默认 minioadmin)
# MINIO_SECRET_KEY=minioadmin
```

### 3. 启动基础设施

```bash
docker-compose up -d
```

**服务端口说明**:

| 服务 | 地址 | 说明 |
|------|------|------|
| MySQL | `localhost:3307` | 用户名/密码: root/root |
| Redis | `localhost:6379` | - |
| Elasticsearch | `localhost:9200` | 混合检索引擎 |
| Kibana | `http://localhost:5601` | ES 可视化管理 |
| MinIO | `localhost:9000` / `9001` | API / Console |
| Kafka | `localhost:9092` | 消息队列 |
| Prometheus | `http://localhost:9090` | 指标采集 |
| Zipkin | `http://localhost:9411` | 链路追踪 |
| Grafana | `http://localhost:3000` | 监控面板 (admin/admin) |

### 4. 启动后端

```bash
# 设置环境变量后启动
# Windows PowerShell:
$env:DEEPSEEK_API_KEY="your-api-key"
$env:JWT_SECRET_KEY="your-jwt-secret"
mvn spring-boot:run

# 或在 IntelliJ IDEA Run Configuration 中配置 Environment Variables
```

后端地址: `http://localhost:8080`

### 5. 启动前端

```bash
cd fronted
pip install -r requirements.txt
streamlit run app.py
```

访问地址: `http://localhost:8501`

---

## 监控与可观测

### Grafana JVM 监控
1. 访问 `http://localhost:3000`，使用 `admin/admin` 登录
2. 添加 Prometheus 数据源，URL 填 `http://prometheus:9090`
3. 导入 Dashboard ID: `4701`（JVM Micrometer 监控面板）

### Zipkin 链路追踪
1. 访问 `http://localhost:9411`
2. 选择 Service Name: `nexus-ai-backend`
3. 点击 "Run Query" 查看请求链路

### Prometheus 指标
*   原始指标端点: `http://localhost:8080/actuator/prometheus`
*   Prometheus 目标状态: `http://localhost:9090/targets`

---

## License

MIT License
