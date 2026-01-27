# Nexus AI - 基于 RAG 的垂直领域知识检索系统

> **专注于垂直领域的高精度知识库问答解决方案**

Nexus AI 是一个现代化、高性能的 **垂直领域知识检索系统**。它基于 RAG（检索增强生成）架构设计，旨在解决通用大模型在特定专业领域（如医疗、法律、金融、工业制造等）中知识幻觉和时效性缺失的问题。

通过深度集成 **Spring Boot 3** 与 **LangChain4j**，结合 **Milvus** 向量数据库与 **Redis** 语义缓存，本项目提供了一套开箱即用的私有化知识库检索方案，确保领域知识的精准召回与高效问答。

---

## 🌟 核心特性

*   **📚 垂直领域专注**
    *   针对特定行业文档（PDF, Docx, Txt）优化的解析与切片策略。
    *   支持专业术语的精准检索，解决通用模型“不懂行”的痛点。

*   **🚀 极致性能架构**
    *   **语义缓存 (Semantic Cache)**: 基于 Redis Stack 的语义相似度匹配，高频问题直接命中缓存，响应速度提升 100x，大幅降低 Token 成本。
    *   **高性能向量库**: 集成 Milvus 处理海量向量数据，支持百万级文档毫秒级检索。

*   **🧠 智能检索增强**
    *   **RAG 架构**: 检索增强生成，基于 factual data 回答，拒绝胡编乱造。
    *   **混合检索**: (计划中) 结合关键词搜索与语义向量检索，提升生僻词召回率。

*   **⚡ 现代化交互体验**
    *   **全链路流式响应 (SSE)**: 后端到前端的全流式打字机效果，极大降低用户感知的首字延迟。
    *   **多模型切换**: 支持在普通对话模型与推理模型（Reasoning Models）间无缝切换。

*   **🛡️ 企业级安全 (In Progress)**
    *   **数据隔离**: 基于 Spring Security + JWT 的用户鉴权体系，确保“谁的数据谁能看”。
    *   **私有化部署**: 核心数据全部本地存储（MinIO + Milvus），数据不出域。

---

## 🛠️ 技术栈

### 后端 (Java)
*   **框架**: Spring Boot 3.3.5
*   **AI 编排**: LangChain4j 0.35.0 (OpenAI / Milvus / Redis 集成)
*   **数据库**: MySQL 8.0 (元数据), MyBatis-Plus 3.5.7
*   **向量库**: Milvus (Zilliz)
*   **缓存**: Redis Stack (支持 Vector Search)
*   **对象存储**: MinIO (原始文档存储)
*   **文档解析**: Apache Tika

### 前端 (Python)
*   **框架**: Streamlit (轻量级交互界面)
*   **通信**: Server-Sent Events (SSE)

---

## 📂 项目结构

```bash
nexus-ai
├── fronted/                 # 演示前端 (Streamlit)
│   └── app.py               # 交互入口
├── src/main/java/com/example/nexusai/
│   ├── config/              # AI/DB/Security 配置
│   ├── controller/          # REST 接口 (Chat, Document)
│   ├── service/             # 核心业务 (RAG流程, 语义缓存, 文档切片)
│   ├── entity/              # 数据库实体
│   └── ...
└── pom.xml                  # Maven 依赖
```

---

## 🚦 快速开始

### 1. 环境准备
确保本地安装并运行以下服务（推荐使用 Docker）：
*   MySQL 8.0
*   Redis Stack (必须支持向量搜索模块)
*   Milvus Standalone
*   MinIO

### 2. 配置应用
修改 `src/main/resources/application.yaml`：
```yaml
spring:
  ai:
    openai:
      api-key: "sk-..."      # 你的 LLM API Key
      base-url: "https://..." 
nexus:
  minio:
    endpoint: "http://localhost:9000"
    # ... 其他配置
```

### 3. 启动后端
```bash
mvn spring-boot:run
```

### 4. 启动前端
```bash
cd fronted
pip install -r requirements.txt
streamlit run app.py
```
访问 `http://localhost:8501` 开始使用。

---

## 🗺️ 开发路线图 (Roadmap)

- [x] **基础 RAG 链路**: 文档上传 -> Tika 解析 -> Milvus 向量化 -> 检索问答
- [x] **性能优化**: 实现基于 Redis 的语义缓存 (Semantic Cache)
- [x] **流式接口**: 修复 SSE 持久化与前端展示问题
- [ ] **鉴权模块**: 集成 Spring Security + JWT，实现用户级数据隔离 (Doing)
- [ ] **容器化**: 提供 Docker Compose 一键部署脚本
- [ ] **混合检索**: 引入关键词检索 (BM25) 增强专业词汇召回

---

## 📄 License
MIT License
