import streamlit as st
import streamlit.components.v1 as components
import requests
import uuid
import time

API_BASE_URL = "http://localhost:8080"

st.set_page_config(page_title="NexusAI", page_icon="icon.png", layout="centered", initial_sidebar_state="collapsed")

def login_api(username, password):
    try:
        resp = requests.post(f"{API_BASE_URL}/auth/login", json={"username": username, "password": password})
        if resp.status_code == 200:
            return True, resp.json().get("token")
        else:
            return False, f"Login failed: {resp.text}"
    except Exception as e:
        return False, str(e)

def register_api(username, password):
    try:
        resp = requests.post(f"{API_BASE_URL}/auth/register", json={"username": username, "password": password})
        if resp.status_code == 200:
            return True, "Registration successful"
        else:
            return False, f"Registration failed: {resp.text}"
    except Exception as e:
        return False, str(e)

# Initialize Session State
if "authenticated" not in st.session_state:
    st.session_state.authenticated = False
if "username" not in st.session_state:
    st.session_state.username = None
if "token" not in st.session_state:
    st.session_state.token = None

# Login/Register Page
if not st.session_state.authenticated:
    st.title("🔐 Nexus AI Login")
    tab1, tab2 = st.tabs(["Login", "Register"])
    
    with tab1:
        with st.form("login_form"):
            username = st.text_input("Username")
            password = st.text_input("Password", type="password")
            submitted = st.form_submit_button("Login")
            if submitted:
                success, result = login_api(username, password)
                if success:
                    st.session_state.authenticated = True
                    st.session_state.username = username
                    st.session_state.token = result
                    st.success("Login successful!")
                    st.rerun()
                else:
                    st.error(result)
    
    with tab2:
        with st.form("register_form"):
            new_user = st.text_input("New Username")
            new_pass = st.text_input("New Password", type="password")
            reg_submitted = st.form_submit_button("Register")
            if reg_submitted:
                success, msg = register_api(new_user, new_pass)
                if success:
                    st.success(msg + ". Please login.")
                else:
                    st.error(msg)
    
    st.stop() # Stop execution here if not authenticated

# ===== Main Application (Only visible after login) =====

if "messages" not in st.session_state:
    st.session_state.messages = []
if "session_id" not in st.session_state:
    st.session_state.session_id = str(uuid.uuid4())
if "current_model" not in st.session_state:
    st.session_state.current_model = "NORMAL"
if "uploaded_files" not in st.session_state:
    st.session_state.uploaded_files = [] 

# Sidebar with User Info
with st.sidebar:
    st.write(f"👤 User: **{st.session_state.username}**")
    if st.button("Logout"):
        st.session_state.authenticated = False
        st.session_state.username = None
        st.session_state.messages = []
        st.rerun()

# ===== 顶部：标题 + 模型选择 =====
col1, col2 = st.columns([1.2, 1])
with col1:
    st.image("icon.png", width=40)
    st.markdown("### NexusAI")
with col2:
    model_val = st.selectbox("Model", ["GPT-Normal", "GPT-Reasoning"], label_visibility="collapsed")
    if model_val:
        st.session_state.current_model = "NORMAL" if "Normal" in model_val else "REASONING"

# ===== 文件上传区：选择文件 + 上传按钮 =====
with st.container():
    up = st.file_uploader("Attach", type=["pdf", "docx", "txt"], label_visibility="collapsed")

    c1, c2 = st.columns([1, 4])
    with c1:
        upload_clicked = st.button("上传文件", use_container_width=True)
    with c2:
        if st.session_state.uploaded_files:
            st.caption("已上传：" + "、".join(st.session_state.uploaded_files[-5:]))

    if upload_clicked:
        if not up:
            st.warning("先选择一个文件再点上传")
        else:
            try:
                headers = {}
                if st.session_state.token:
                    headers["Authorization"] = f"Bearer {st.session_state.token}"
                
                files = {"file": (up.name, up.getvalue(), up.type or "application/octet-stream")}
                r = requests.post(f"{API_BASE_URL}/docs/upload", files=files, headers=headers, timeout=120)
                if r.status_code == 200:
                    st.success(f"✅ 上传成功：{up.name}")
                    st.session_state.uploaded_files.append(up.name)
                else:
                    st.error(f"❌ 上传失败：{r.status_code} {r.text}")
            except Exception as e:
                st.error(f"❌ 上传异常：{e}")

st.divider()

# ===== 渲染历史 =====
for msg in st.session_state.messages:
    with st.chat_message(msg["role"]):
        st.markdown(msg["content"])

# 自动滚到底（可选）
components.html("<script>window.scrollTo(0, document.body.scrollHeight);</script>", height=0)

# ===== 输入 + 真实流式 SSE =====
user_input = st.chat_input("输入问题，Enter 发送…")

if user_input and user_input.strip():
    st.session_state.messages.append({"role": "user", "content": user_input})
    with st.chat_message("user"):
        st.markdown(user_input)

    with st.chat_message("assistant"):
        placeholder = st.empty()
        full_response = ""
        start_t = time.time()

        try:
            params = {
                "query": user_input,
                "sessionId": st.session_state.session_id,
                "modelType": st.session_state.current_model,  
            }
            headers = {"Accept": "text/event-stream"}
            if st.session_state.token:
                headers["Authorization"] = f"Bearer {st.session_state.token}"

            with requests.get(f"{API_BASE_URL}/ai/stream", params=params, headers=headers, stream=True, timeout=(5, 120)) as resp:
                resp.raise_for_status()

                for raw_line in resp.iter_lines(decode_unicode=False, chunk_size=1):
                    if not raw_line:
                        continue
                    line = raw_line.decode("utf-8", errors="ignore")  
                    if not line:
                        continue
                    if line.startswith("data:"):
                        data = line[5:].lstrip()
                    else:
                        data = line

                    if data.strip() in ("[DONE]", "__DONE__"):
                        break

                    full_response += data
                    placeholder.markdown(full_response + "▌")

            dur = int((time.time() - start_t) * 1000)
            placeholder.markdown(full_response + f"\n\n<small>⏱️ {dur}ms</small>", unsafe_allow_html=True)

            st.session_state.messages.append({"role": "assistant", "content": full_response})
        except Exception as e:
            st.error(f"❌ 流式请求失败：{e}")
