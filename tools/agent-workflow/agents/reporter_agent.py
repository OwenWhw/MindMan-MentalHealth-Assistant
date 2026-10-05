"""Reporter Agent：汇总全流程产物，生成 Markdown 报告 + JSON 结果（不用 LLM，保证稳定）。"""

import json
import os
import time

from core.agent import BaseAgent


class ReporterAgent(BaseAgent):
    name = "reporter"
    role = "报告汇总 Agent"
    uses_llm = False

    def run(self, ctx):
        out_dir = ctx.config.get("run", {}).get("outputDir", "docs/agent-reports")
        os.makedirs(out_dir, exist_ok=True)
        stamp = time.strftime("%Y%m%d-%H%M%S")

        passed = sum(1 for r in ctx.results if r["result"] == "PASS")
        failed = len(ctx.results) - passed
        endpoints = ctx.extras.get("endpoints", [])
        analyses = {a["id"]: a for a in ctx.analyses}
        llm_cases = sum(1 for c in ctx.cases if c.get("source") == "llm")

        md = []
        md.append("# 多 Agent 接口测试报告")
        md.append("")
        md.append("> 生成时间：%s ｜ 目标服务：%s ｜ 驱动模型：%s"
                  % (ctx.started_at, ctx.config.get("baseUrl"),
                     ctx.config.get("llm", {}).get("model")))
        try:
            from datetime import datetime
            elapsed = (datetime.now() - datetime.strptime(ctx.started_at, "%Y-%m-%d %H:%M:%S")).total_seconds()
        except Exception:
            elapsed = 0
        chain = " → ".join(a for a in ctx.config.get("run", {}).get("agents", [])) or "spec → executor → analysis → reporter"
        md.append("> 编排链：%s ｜ 已用耗时 %.1fs" % (chain, elapsed))
        md.append("")
        md.append("## 一、总览")
        md.append("")
        md.append("| 指标 | 数值 |")
        md.append("| --- | --- |")
        md.append("| 静态解析端点数 | %d |" % len(endpoints))
        md.append("| 生成本次用例数（LLM %d / 规则 %d） | %d |"
                  % (llm_cases, len(ctx.cases) - llm_cases, len(ctx.cases)))
        md.append("| 执行通过 | %d |" % passed)
        md.append("| 执行失败 | %d |" % failed)
        md.append("| 通过率 | %.1f%% |" % (passed * 100.0 / max(len(ctx.results), 1)))
        md.append("")

        md.append("## 二、用例执行明细")
        md.append("")
        md.append("| 用例 | 请求 | 期望 | 实际 | 耗时 | 结果 |")
        md.append("| --- | --- | --- | --- | --- | --- |")
        for r in ctx.results:
            md.append("| %s | `%s` | %s | %s | %dms | %s |"
                      % (r["name"][:34], r["request"], r["expect"], r["actual"], r["ms"], r["result"]))
        md.append("")

        if analyses:
            md.append("## 三、失败归因（Analysis Agent）")
            md.append("")
            md.append("| 用例 | 归因类别 | 原因 | 建议 |")
            md.append("| --- | --- | --- | --- |")
            for r in ctx.results:
                if r["result"] == "FAIL" and r["id"] in analyses:
                    a = analyses[r["id"]]
                    md.append("| %s | **%s** | %s | %s |"
                              % (r["id"], a["category"], a["reason"], a["suggestion"]))
            md.append("")
            # 归因分布
            dist = {}
            for a in ctx.analyses:
                dist[a["category"]] = dist.get(a["category"], 0) + 1
            md.append("归因分布：" + "、".join("%s %d" % (k, v) for k, v in dist.items()))
            md.append("")

        md.append("## 四、Agent 执行轨迹")
        md.append("")
        md.append("| 时间 | Agent | 事件 |")
        md.append("| --- | --- | --- |")
        for t in ctx.trace:
            md.append("| %s | %s | %s |" % (t["t"], t["agent"], t["message"]))
        md.append("")

        md.append("## 五、端点清单（静态解析，非 LLM 生成）")
        md.append("")
        md.append("| 控制器 | 方法 | 路径 |")
        md.append("| --- | --- | --- |")
        for e in endpoints:
            md.append("| %s | %s | `%s` |" % (e["controller"], e["method"], e["path"]))
        md.append("")

        md_path = os.path.join(out_dir, "agent-report-%s.md" % stamp)
        json_path = os.path.join(out_dir, "agent-result-%s.json" % stamp)
        with open(md_path, "w", encoding="utf-8") as f:
            f.write("\n".join(md))
        with open(json_path, "w", encoding="utf-8") as f:
            json.dump({
                "generatedAt": ctx.started_at, "baseUrl": ctx.config.get("baseUrl"),
                "endpoints": len(endpoints), "cases": ctx.cases,
                "results": ctx.results, "analyses": ctx.analyses, "trace": ctx.trace,
            }, f, ensure_ascii=False, indent=2)

        ctx.extras["reportMd"] = md_path
        ctx.extras["reportJson"] = json_path
        ctx.log(self.name, "报告已生成：%s" % md_path)
        ctx.log(self.name, "原始结果：%s" % json_path)
