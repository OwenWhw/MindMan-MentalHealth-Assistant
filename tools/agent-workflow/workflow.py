#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""MindMan 多 Agent 测试工作流 · 入口

用法：
    python tools/agent-workflow/workflow.py                    # 按 config.json 跑默认链
    python tools/agent-workflow/workflow.py --agents spec,executor,analysis,reporter
    python tools/agent-workflow/workflow.py --no-llm           # 完全降级为规则模式（不调模型）
    python tools/agent-workflow/workflow.py --agents spec,executor,reporter,review
    python tools/agent-workflow/workflow.py --list             # 查看可用 Agent

设计：Agent 链可配置、可插拔。新增一个 Agent 只需在 AGENT_REGISTRY 注册，
无需改动编排器或其它 Agent。
"""

import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from core.coordinator import Coordinator          # noqa: E402
from core.llm import LLMClient                    # noqa: E402
from agents.spec_agent import SpecAgent           # noqa: E402
from agents.executor_agent import ExecutorAgent   # noqa: E402
from agents.analysis_agent import AnalysisAgent   # noqa: E402
from agents.reporter_agent import ReporterAgent   # noqa: E402
from agents.review_agent import ReviewAgent       # noqa: E402

AGENT_REGISTRY = {
    "spec": SpecAgent,           # 读源码 → 生成用例
    "executor": ExecutorAgent,   # 执行用例
    "analysis": AnalysisAgent,   # 失败归因
    "reporter": ReporterAgent,   # 汇总报告
    "review": ReviewAgent,       # 代码审查（可选）
}

DEFAULT_CHAIN = ["spec", "executor", "analysis", "reporter"]


def load_config(path, no_llm=False):
    if not os.path.exists(path):
        raise SystemExit("配置文件不存在：%s" % path)
    cfg = json.load(open(path, encoding="utf-8"))
    # 相对路径 → 以仓库根目录为基准（config 位于 tools/agent-workflow/）
    repo_root = os.path.abspath(os.path.join(HERE, "..", ".."))
    for key in ("controllerDir", "backendDir"):
        p = cfg["target"].get(key)
        if p and not os.path.isabs(p):
            cfg["target"][key] = os.path.join(repo_root, p)
    out = cfg["run"].get("outputDir")
    if out and not os.path.isabs(out):
        cfg["run"]["outputDir"] = os.path.join(repo_root, out)
    if no_llm:
        cfg.setdefault("llm", {})["enabled"] = False
    return cfg


def main():
    ap = argparse.ArgumentParser(description="多 Agent 测试工作流")
    ap.add_argument("--config", default=os.path.join(HERE, "config.json"))
    ap.add_argument("--agents", default="")
    ap.add_argument("--no-llm", action="store_true", help="禁用 LLM，纯规则模式")
    ap.add_argument("--list", action="store_true", help="列出可用 Agent")
    args = ap.parse_args()

    if args.list:
        print("可用 Agent：")
        for k, v in AGENT_REGISTRY.items():
            print("  %-10s %s" % (k, v.role))
        print("\n默认链：%s" % " → ".join(DEFAULT_CHAIN))
        return

    cfg = load_config(args.config, no_llm=args.no_llm)
    chain = [a.strip() for a in args.agents.split(",") if a.strip()] or \
            cfg.get("run", {}).get("agents") or DEFAULT_CHAIN
    unknown = [a for a in chain if a not in AGENT_REGISTRY]
    if unknown:
        raise SystemExit("未知 Agent：%s（可用：%s）" % (unknown, list(AGENT_REGISTRY)))

    llm = LLMClient(cfg.get("llm"))
    agents = [AGENT_REGISTRY[name](llm=llm, cfg=cfg) for name in chain]
    ctx = Coordinator(cfg, agents, llm=llm).run()

    # 退出码：有失败用例时非 0，便于 CI 集成
    failed = sum(1 for r in ctx.results if r["result"] == "FAIL")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
