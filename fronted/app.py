import streamlit as st
import requests
import uuid

# ================= 配置区 =================
# 这里的端口必须和你后端 Spring Boot 的端口一致 (根据你的日志是 8088)
API_BASE_URL = "http://localhost:8088/api"

st.set_page_config(
    page_title="NexusAI 智能中台",
    page_icon="🤖",
    layout="wide"
)

# ================= 状态管理 =================
if "messages" not in st.session_state:
    st.session_state.messages = []

if "current_model" not in st.session_state:
    st.session_state.current_model = "NORMAL"

# 新增：生成并保持 Session ID
if "session_id" not in st.session_state:
    st.session_state.session_id = str(uuid.uuid4()) # 生成一个唯一ID

# ================= 侧边栏：设置与上传 =================
with st.sidebar:
    st.title("🎛️ 控制台")

    st.markdown("### 1. 模型选择")
    # 旧版 Streamlit 也支持 radio
    model_option = st.radio(
        "选择思考模式:",
        ("普通对话 (Normal)", "深度思考 (Reasoning)"),
        index=0 if st.session_state.current_model == "NORMAL" else 1
    )
    st.session_state.current_model = "NORMAL" if "Normal" in model_option else "REASONING"

    st.markdown("---")

    st.markdown("### 2. 知识库投喂")
    uploaded_file = st.file_uploader(
        "上传文档 (PDF/Word)",
        type=['pdf', 'docx', 'txt', 'md'],
        help="选择文件后点击下方按钮上传"
    )

    # 始终显示按钮，但根据状态启用/禁用
    upload_button = st.button(
        "📤 确认上传并入库",
        disabled=(uploaded_file is None),
        key="upload_btn"
    )

    if upload_button and uploaded_file is not None:
        with st.spinner("正在上传并解析向量..."):
            try:
                files = {"file": (uploaded_file.name, uploaded_file, uploaded_file.type)}
                response = requests.post(f"{API_BASE_URL}/docs/upload", files=files)

                if response.status_code == 200:
                    st.success(f"✅ {response.text}")
                else:
                    st.error(f"❌ 上传失败: {response.text}")
            except Exception as e:
                st.error(f"❌ 连接错误: {str(e)}")

# ================= 主界面：聊天窗口 (旧版兼容写法) =================
st.title("🤖 NexusAI 企业智能助手")
st.caption("基于 Java Spring AI + LangChain4j + Milvus 构建")

# 1. 展示历史消息 (使用普通 Markdown 渲染)
# 这一块为了模仿聊天气泡，我们可以用 st.info (代表 AI) 和 st.write (代表用户)
container = st.container()
with container:
    for message in st.session_state.messages:
        role = message["role"]
        content = message["content"]

        if role == "user":
            # 用户消息
            st.markdown(f"**🧑‍💻 You:**")
            st.write(content)
        else:
            # AI 消息
            st.markdown(f"**🤖 AI ({message.get('model', 'Unknown')}):**")
            # 渲染 AI 回复
            if "duration" in message:
                st.info(content)
                st.caption(f"⏱️ 耗时: {message['duration']}ms")
            else:
                st.info(content)
        st.markdown("---") # 分割线

# 2. 处理用户输入 (使用 Form 表单替代 chat_input)
# 这在旧版本 Streamlit 中是最稳妥的做法
with st.form(key='chat_form', clear_on_submit=True):
    user_input = st.text_input("请输入您的问题...", key="input_box")
    submit_button = st.form_submit_button("发送 🚀")

    if submit_button and user_input:
        # 显示用户消息 (保存到状态，下一次刷新时会在上面显示)
        st.session_state.messages.append({"role": "user", "content": user_input})

        # 立即显示一个加载提示
        with st.spinner("🤔 AI 正在思考中..."):
            try:
                # 构造请求参数
                params = {
                    "query": user_input,
                    "modelType": st.session_state.current_model,
                    "sessionId": st.session_state.session_id
                }

                # 发起请求
                response = requests.get(f"{API_BASE_URL}/ai/chat", params=params)

                if response.status_code == 200:
                    data = response.json()
                    if data['success']:
                        answer = data['answer']
                        duration = data['duration']
                        model_used = data['modelName']

                        # 保存助手回复到历史
                        st.session_state.messages.append({
                            "role": "assistant",
                            "content": answer,
                            "duration": duration,
                            "model": model_used
                        })
                        # 强制刷新页面以显示最新消息
                        st.experimental_rerun()
                    else:
                        st.error(f"后端返回错误: {data.get('error')}")
                else:
                    st.error(f"HTTP 错误: {response.status_code} - {response.text}")

            except Exception as e:
                st.error(f"请求异常: {str(e)}")