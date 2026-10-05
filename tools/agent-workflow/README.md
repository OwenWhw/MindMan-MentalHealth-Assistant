# MindMan 多 Agent 测试工作流

一个**本地可运行、零第三方依赖**（纯 Python 标准库）的多 Agent 编排框架，用本地 Ollama 模型驱动，
自动化完成「接口测试」全流程：**读源码生成用例 → 执行 → 失败归因 → 输出报告**。

```
spec ──→ executor ──→ analysis ──→ reporter
读 Controller    执行 HTTP     失败归因      汇总报告
生成用例        记录结果      (LLM)        (Markdown+JSON)
 (LLM)
```

## 快速开始

```bash
# 1. 确保后端与 Ollama 已启动
# 2. 跑默认链（LLM 生成用例 + 归因）
python tools/agent-workflow/workflow.py

# 纯规则模式（不调用模型，秒级完成，适合 CI）
python tools/agent-workflow/workflow.py --no-llm

# 自定义 Agent 链
python tools/agent-workflow/workflow.py --agents spec,executor,reporter,review

# 查看可用 Agent
python tools/agent-workflow/workflow.py --list
```

退出码：有失败用例时返回 1，便于接入 CI。

## 目录结构

```
tools/agent-workflow/
├── workflow.py              # 入口：装配 Agent 链并启动编排
├── config.json              # 目标地址 / 模型 / 鉴权 / 输出目录
├── core/
│   ├── agent.py             # BaseAgent + SharedContext（Agent 间共享黑板）
│   ├── coordinator.py       # 编排器：顺序调度 + 耗时统计 + 异常隔离
│   ├── llm.py               # Ollama / OpenAI 兼容客户端（JSON 强约束 + 降级）
│   └── http.py              # HTTP 工具 + 登录鉴权 + 业务码解析
└── agents/
    ├── spec_agent.py        # ① 静态解析 Controller 端点 + LLM 生成用例
    ├── executor_agent.py    # ② 确定性执行用例（不用 LLM）
    ├── analysis_agent.py    # ③ 失败归因（LLM + 规则兜底）
    ├── reporter_agent.py    # ④ 汇总 Markdown/JSON 报告
    └── review_agent.py      # ⑤ 代码审查（规则版，可选扩展）
```

## 设计要点

| 设计决策 | 原因 |
| --- | --- |
| **LLM 只用于模糊环节**（生成用例、归因），执行环节纯确定性 | 7B 本地模型输出不稳定，绝不能让它决定"请求发不发" |
| **端点清单用正则静态解析**，不交给 LLM | 避免模型幻觉出不存在的接口（脚本会丢弃清单外的路径） |
| **所有 LLM 调用失败自动降级**为规则方案 | 模型没启动/超时也不会中断流水线 |
| **Agent 通过 SharedContext 通信**，互不直接调用 | 新增 Agent（功能测试/代码审查）无需改动已有 Agent |
| **项目约定内置**（HTTP 恒 200、业务码在 body.code） | 否则 LLM 会用 201/204 造成大量假失败 |
| **单 Agent 异常隔离** | 一个 Agent 崩溃不影响整条链，报告照常产出 |

## 扩展新 Agent

1. 新建 `agents/xxx_agent.py`，继承 `BaseAgent`，实现 `run(ctx)`
2. 在 `workflow.py` 的 `AGENT_REGISTRY` 注册一行
3. 用 `--agents spec,executor,xxx,reporter` 运行

已预留方向：**功能测试 Agent**（浏览器走查 + 截图留证）、**代码审查 Agent**（当前为规则版，可接云端模型做深度审查）、**文档一致性 Agent**（核对简历/文档描述与实现是否一致）。

## 配置说明（config.json）

| 字段 | 说明 |
| --- | --- |
| `baseUrl` | 被测服务地址 |
| `llm.model` | 驱动模型（默认 `qwen2.5:7b`）；`enabled=false` 等价于 `--no-llm` |
| `target.controllerDir` | Controller 源码目录（相对仓库根，端点解析来源） |
| `target.auth` | 登录路径与账号密码 |
| `run.maxCases` | 单次生成用例上限（控制 LLM 耗时） |
| `run.outputDir` | 报告输出目录 |

## 实测数据（MindMan 项目）

| 模式 | 用例数 | 通过 | 耗时 | 说明 |
| --- | --- | --- | --- | --- |
| 规则模式 `--no-llm` | 12 | 10 | 0.5s | 每端点 1 条正向 |
| LLM 模式（qwen2.5:7b） | 12 | 10 | 33s | 用例名/期望值由模型生成，含异常用例 |

静态解析覆盖：**55 个端点**（13 个 Controller）。
