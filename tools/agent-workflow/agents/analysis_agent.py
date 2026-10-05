"""Analysis Agent：对失败用例做归因（LLM 判断 + 规则兜底）。

归因类别固定四类，便于统计与后续处理：
  代码缺陷 / 用例问题 / 环境问题 / 预期外行为
"""

from core.agent import BaseAgent


class AnalysisAgent(BaseAgent):
    name = "analysis"
    role = "失败归因 Agent"
    uses_llm = True

    SYSTEM = (
        "你是测试结果分析专家。针对失败的接口用例判断根因，只能从四类中选择："
        "代码缺陷、用例问题、环境问题、预期外行为。"
        "【重要背景】该服务 HTTP 状态码恒为 200，业务码在 body.code。因此："
        "1) 实际 code=400 且提示『XX不能为空』『格式不正确』→ 归为【用例问题】（用例入参不全）。"
        "2) 期望 expectStatus 写了非 200（如 201）而实际 HTTP 200 → 归为【用例问题】（用例期望值设置错误）。"
        "3) 实际 HTTP 500 或 code=500 → 归为【代码缺陷】。"
        "4) 连接失败/超时 → 归为【环境问题】。"
        "5) 实际 code=401 且用例未带 token → 归为【用例问题】。"
        "严格输出 JSON：{\"analyses\":[{\"id\":\"用例ID\",\"category\":\"\","
        "\"reason\":\"不超过60字\",\"suggestion\":\"不超过40字\"}]}"
    )

    def run(self, ctx):
        failed = [r for r in ctx.results if r["result"] == "FAIL"]
        if not failed:
            ctx.log(self.name, "无失败用例，跳过归因")
            ctx.analyses = []
            return

        ctx.log(self.name, "待归因失败用例 %d 条" % len(failed))
        analyses = self._analyze_with_llm(ctx, failed)
        if not analyses:
            analyses = [self._heuristic(f) for f in failed]
            ctx.log(self.name, "降级为规则归因")

        by_id = {a["id"]: a for a in analyses if isinstance(a, dict) and a.get("id")}
        # 补齐 LLM 漏掉的用例
        for f in failed:
            if f["id"] not in by_id:
                by_id[f["id"]] = self._heuristic(f)
        ctx.analyses = list(by_id.values())
        for a in ctx.analyses:
            ctx.log(self.name, "· %s → %s：%s" % (a["id"], a["category"], a["reason"][:40]))

    def _analyze_with_llm(self, ctx, failed):
        if not self.llm:
            return []
        lines = ["用例ID | 请求 | 期望 | 实际 | 响应片段"]
        for f in failed[:10]:
            lines.append("%s | %s | %s | %s | %s" % (
                f["id"], f["request"], f["expect"], f["actual"], f["responsePreview"][:80]))
        parsed = self.llm.chat_json(self.SYSTEM, "\n".join(lines))
        if not parsed:
            return []
        items = parsed.get("analyses") if isinstance(parsed, dict) else parsed
        out = []
        if isinstance(items, list):
            for it in items:
                if isinstance(it, dict) and it.get("id"):
                    out.append({
                        "id": str(it["id"]),
                        "category": str(it.get("category", "预期外行为")),
                        "reason": str(it.get("reason", ""))[:120],
                        "suggestion": str(it.get("suggestion", ""))[:80],
                    })
        return out

    @staticmethod
    def _heuristic(f):
        """规则兜底：按状态码/错误特征粗判"""
        actual = f.get("actual", "")
        if f.get("error"):
            return {"id": f["id"], "category": "环境问题",
                    "reason": "请求异常：%s" % str(f["error"])[:60],
                    "suggestion": "确认目标服务是否启动、端口与代理设置"}
        if "HTTP 500" in actual:
            return {"id": f["id"], "category": "代码缺陷",
                    "reason": "服务端 500，可能为空指针或未处理异常",
                    "suggestion": "查后端日志定位异常堆栈"}
        if "HTTP 404" in actual or "code 404" in actual:
            return {"id": f["id"], "category": "用例问题",
                    "reason": "资源不存在，可能是用例数据（如会话ID）无效",
                    "suggestion": "让用例先创建依赖资源再断言"}
        if "code 400" in actual or "HTTP 400" in actual:
            return {"id": f["id"], "category": "用例问题",
                    "reason": "参数校验未通过，用例入参不符合接口约束",
                    "suggestion": "按 DTO 约束修正用例入参"}
        if "HTTP 401" in actual:
            return {"id": f["id"], "category": "用例问题",
                    "reason": "鉴权失败（token 缺失/失效）",
                    "suggestion": "确认用例是否需要鉴权开关 needsAuth"}
        return {"id": f["id"], "category": "预期外行为",
                "reason": "期望与实际不一致，需人工确认", "suggestion": "人工复核接口契约"}
