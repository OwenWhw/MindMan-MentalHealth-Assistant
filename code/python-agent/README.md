# Python 情绪花园 Agent

这是 MindMan 的一个可选 Python 服务，只处理用户主动提交的**当前一条**情绪记录。
Spring Boot 继续负责登录鉴权、用户数据和对前端的 `/api/emotion/garden/insight` 接口；
Python 调用 OpenAI 兼容模型，校验引用与参考分后返回结构化回看。
Python 服务不可用或结果无效时，Java 会使用现有的回看逻辑。

## 本地启动

需要 Python 3.11+，以及能访问的 OpenAI 兼容模型接口。以下命令在本目录执行：

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
$env:MINDMAN_PY_MODEL_BASE_URL = 'http://127.0.0.1:11434/v1'
$env:MINDMAN_PY_MODEL = 'qwen2.5:7b'
.\.venv\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8091
```

使用云端 OpenAI 兼容接口时，另设 `MINDMAN_PY_MODEL_API_KEY`，并把
`MINDMAN_PY_MODEL_BASE_URL` 和 `MINDMAN_PY_MODEL` 改成对应地址与模型。
可用 `http://127.0.0.1:8091/health` 检查 Python 服务是否已启动；这不代表模型一定可用。

另开终端，在 `code/backend` 目录启用 Java 到 Python 的调用：

```powershell
$env:MINDMAN_PY_AGENT_ENABLED = 'true'
$env:MINDMAN_PY_AGENT_BASE_URL = 'http://127.0.0.1:8091'
mvn spring-boot:run
```

如需给内部调用加令牌，两个进程均设置相同的 `MINDMAN_PY_AGENT_TOKEN`。
Java 调用超时可用 `MINDMAN_PY_AGENT_TIMEOUT_SECONDS` 调整，默认 50 秒。

## 验证

```powershell
python -m unittest test_garden_agent.py
```

然后在网站的情绪花园里写一条具体记录，点击「分析这条记录」。
此操作不写入或修改用户自评分。Python 仅接收本条记录的心情、文字和触发因素。
该模块暂不接管对话流式接口。
