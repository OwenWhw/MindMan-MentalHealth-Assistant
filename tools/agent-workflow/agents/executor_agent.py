"""Executor Agent：确定性执行接口用例（不使用 LLM）。

判定规则（与项目约定一致）：
- HTTP 状态码优先比对
- 项目 HTTP 恒 200、业务码在 body.code，故同时校验 code
- 记录耗时，便于后续性能观察
"""

from core.agent import BaseAgent
from core import http


class ExecutorAgent(BaseAgent):
    name = "executor"
    role = "接口执行 Agent"
    uses_llm = False

    def run(self, ctx):
        base = ctx.config["baseUrl"]
        auth = ctx.config["target"]["auth"]
        timeout = int(ctx.config.get("run", {}).get("httpTimeout", 30))

        # 登录拿 token（执行链路的第一步）
        ctx.token = http.login(base, auth)
        ctx.log(self.name, "鉴权：%s" % ("已获取 token" if ctx.token else "登录失败/无需鉴权"))

        results = []
        for case in ctx.cases:
            token = ctx.token if case.get("needsAuth", True) else None
            r = http.request(
                base, case["method"], case["path"],
                body=case.get("body") if case["method"] in ("POST", "PUT", "PATCH") else None,
                token=token, timeout=timeout)

            actual_status = r["status"]
            actual_code = http.biz_code(r)
            expect_status = case.get("expectStatus", 200)
            expect_code = case.get("expectCode", 200)

            passed = actual_status == expect_status
            if passed and expect_code is not None and actual_code is not None:
                passed = actual_code == expect_code
            if r.get("error"):
                passed = False

            item = {
                "id": case["id"], "name": case["name"],
                "request": "%s %s" % (case["method"], case["path"]),
                "expect": "HTTP %s / code %s" % (expect_status, expect_code),
                "actual": "HTTP %s / code %s" % (actual_status, actual_code),
                "ms": r["ms"],
                "result": "PASS" if passed else "FAIL",
                "error": r.get("error"),
                "responsePreview": http.body_preview(r),
                "caseSource": case.get("source", "llm"),
                "needsAuth": case.get("needsAuth", True),
            }
            results.append(item)
            ctx.log(self.name, "[%s] %-30s %s (%dms)" % (
                item["result"], item["name"][:30], item["request"], item["ms"]))

        ctx.results = results
        p = sum(1 for x in results if x["result"] == "PASS")
        ctx.log(self.name, "执行完成：%d 条，通过 %d，失败 %d" % (len(results), p, len(results) - p))
