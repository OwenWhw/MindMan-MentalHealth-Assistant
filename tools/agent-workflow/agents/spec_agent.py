"""Spec Agent：从后端 Controller 源码中提取真实端点，并由 LLM 生成接口测试用例。

混合策略（可靠性与智能兼顾）：
1. 正则静态解析 @RequestMapping/@GetMapping/... → 得到「真实存在的端点」清单（确定性，不会幻觉）
2. 从清单中挑选核心端点，交给 LLM 生成用例（含正向 + 边界/异常）
3. LLM 不可用时降级为「每个端点一条正向用例」
"""

import json
import os
import re

from core.agent import BaseAgent

# 方法注解 → HTTP 方法（含同一行/跨行参数，DOTALL 处理多行注解）
METHOD_ANN_RE = re.compile(
    r"@(Get|Post|Put|Delete|Patch)Mapping\s*(?:\(\s*(.*?)\s*\))?", re.S)
CLASS_REQ_RE = re.compile(r"@RequestMapping\(\s*[\"']([^\"']+)[\"']")
QUOTED_RE = re.compile(r"[\"']([^\"']*)[\"']")
PUBLIC_METHOD_RE = re.compile(r"public\s+[\w<>,\[\]\s\.]+\s+(\w+)\s*\(")


def _path_param_replace(path):
    """把 REST 路径中的占位符替换为安全测试值：{id}→1，其它→test"""
    out = re.sub(r"\{(\w+)\}", lambda m: "1" if m.group(1).lower() in ("id", "sessionid", "flowerid", "articleid", "userid") else "test", path)
    return out


def extract_endpoints(controller_dir):
    """静态解析目录下所有 Controller，返回端点清单（确定性，不依赖 LLM）。"""
    endpoints = []
    if not os.path.isdir(controller_dir):
        return endpoints
    for fname in sorted(os.listdir(controller_dir)):
        if not fname.endswith(".java"):
            continue
        path = os.path.join(controller_dir, fname)
        try:
            src = open(path, encoding="utf-8", errors="ignore").read()
        except Exception:
            continue
        cls_match = CLASS_REQ_RE.search(src)
        prefix = cls_match.group(1) if cls_match else ""
        class_name = fname[:-5]

        for m in METHOD_ANN_RE.finditer(src):
            http_method = m.group(1).upper()
            args = m.group(2) or ""
            # 取注解参数中第一个字符串字面量作为子路径（跳过 produces/consumes 等）
            sub = ""
            value_part = args.split("produces")[0].split("consumes")[0]
            q = QUOTED_RE.search(value_part)
            if q:
                sub = q.group(1)
            if sub and not sub.startswith("/"):
                sub = "/" + sub
            full = (prefix.rstrip("/") + sub) or prefix
            endpoints.append({
                "controller": class_name,
                "method": http_method,
                "path": full,
                "pathTemplate": full,
                "testPath": _path_param_replace(full),
            })
    # 去重（同 path+method 只保留一次）
    seen, uniq = set(), []
    for e in endpoints:
        k = (e["method"], e["path"])
        if k not in seen:
            seen.add(k)
            uniq.append(e)
    return uniq


class SpecAgent(BaseAgent):
    name = "spec"
    role = "测试用例生成 Agent"
    uses_llm = True

    CORE_HINTS = [
        "/api/auth/login", "/api/auth/me", "/api/emotion/garden",
        "/api/knowledge/article/page", "/api/chat/models", "/api/chat/sessions",
        "/api/analysis/overview", "/api/admin/users",
    ]

    def run(self, ctx):
        controller_dir = ctx.config["target"]["controllerDir"]
        endpoints = extract_endpoints(controller_dir)
        ctx.extras["endpoints"] = endpoints
        ctx.log(self.name, "静态解析到 %d 个端点（来自 %s）" % (
            len(endpoints), os.path.basename(controller_dir)))
        for e in endpoints[:6]:
            ctx.log(self.name, "  · %-6s %s" % (e["method"], e["path"]))

        if not endpoints:
            ctx.log(self.name, "未解析到端点，终止用例生成")
            return

        max_cases = int(ctx.config.get("run", {}).get("maxCases", 12))
        # 优先挑选核心端点（覆盖各模块），再补充其余
        picked, others = [], []
        for e in endpoints:
            (picked if e["path"] in self.CORE_HINTS else others).append(e)
        candidates = (picked + others)[:max_cases]
        ctx.extras["picked_endpoints"] = candidates

        cases = self._gen_with_llm(ctx, candidates)
        if not cases:
            cases = self._fallback_cases(candidates)
            ctx.log(self.name, "降级为规则用例（每端点 1 条正向）")
        ctx.cases = cases
        ctx.log(self.name, "生成 %d 条测试用例" % len(cases))

    # ---------- LLM 生成 ----------

    def _gen_with_llm(self, ctx, candidates):
        if not self.llm:
            return []
        ep_text = "\n".join("- %s %s（%s）" % (e["method"], e["testPath"], e["controller"]) for e in candidates)
        system = (
            "你是资深接口测试工程师。根据给定的 Spring Boot 端点清单设计接口测试用例。"
            "严格输出 JSON，格式：{\"cases\":[{\"id\":\"\",\"name\":\"\",\"method\":\"GET\","
            "\"path\":\"\",\"body\":{},\"expectStatus\":200,\"expectCode\":200,\"needsAuth\":true}]}。"
            "【项目约定-必须遵守】"
            "1) 该服务 HTTP 状态码恒为 200，业务错误码放在 body.code。因此 expectStatus 一律写 200"
            "（不要写 201/204/400）；参数校验失败时 expectCode 写 400，未认证时写 401，资源不存在写 404。"
            "2) path 必须来自给定清单，不得编造。"
            "3) POST/PUT 用例必须在 body 中给出完整必填字段（如 username/password/phone/content/emotion），"
            "字段不全会导致参数校验失败。"
            "4) 每个端点 1 条正向用例；对登录等校验类端点可加 1 条异常用例。"
        )
        user = "端点清单：\n%s\n\n请生成不超过 %d 条用例。" % (ep_text, len(candidates))
        parsed = self.llm.chat_json(system, user)
        if not parsed:
            return []
        raw_cases = parsed.get("cases") if isinstance(parsed, dict) else parsed
        if not isinstance(raw_cases, list):
            return []

        valid_paths = {e["testPath"] for e in candidates}
        cases = []
        for i, c in enumerate(raw_cases, 1):
            if not isinstance(c, dict):
                continue
            path = str(c.get("path", ""))
            if path not in valid_paths:          # 防幻觉：路径必须在真实清单内
                ctx.log(self.name, "丢弃幻觉路径：%s" % path)
                continue
            cases.append({
                "id": str(c.get("id") or "AI-%02d" % i),
                "name": str(c.get("name") or path),
                "method": str(c.get("method", "GET")).upper(),
                "path": path,
                "body": c.get("body") if isinstance(c.get("body"), dict) else {},
                "expectStatus": int(c.get("expectStatus", 200)),
                "expectCode": c.get("expectCode", 200),
                "needsAuth": bool(c.get("needsAuth", True)),
                "source": "llm",
            })
        return cases

    # ---------- 规则降级 ----------

    def _fallback_cases(self, candidates):
        cases = []
        for i, e in enumerate(candidates, 1):
            needs_auth = "/login" not in e["testPath"] and "/register" not in e["testPath"]
            body = {}
            if e["method"] in ("POST", "PUT") and "login" in e["testPath"]:
                body = {"username": "admin", "password": "123456"}
            cases.append({
                "id": "RULE-%02d" % i,
                "name": "%s %s 正向" % (e["method"], e["testPath"]),
                "method": e["method"],
                "path": e["testPath"],
                "body": body,
                "expectStatus": 200,
                "expectCode": 200,
                "needsAuth": needs_auth,
                "source": "rule",
            })
        return cases
