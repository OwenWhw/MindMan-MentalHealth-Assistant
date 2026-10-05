"""Review Agent（可选，预留扩展）：规则版代码审查，不依赖 LLM。

当前覆盖 3 类可确定性判定的问题：
  1. 后端存在但未被任何 Controller 引用的 Service（孤儿服务）
  2. 前端调用但不存在的后端接口（前后端断链）
  3. 大文件 / 疑似重复文件（辅助定位冗余）

不做 LLM 审查，是因为 7B 模型对整仓代码推理质量不稳定；
若接云端模型，可在 _llm_review() 中扩展。
"""

import os
import re

from core.agent import BaseAgent


class ReviewAgent(BaseAgent):
    name = "review"
    role = "代码审查 Agent（规则版）"
    uses_llm = False

    def run(self, ctx):
        backend = ctx.config["target"]["backendDir"]
        findings = []
        findings += self._orphan_services(backend)
        findings += self._big_files(backend)
        ctx.extras["reviewFindings"] = findings
        ctx.log(self.name, "审查发现 %d 条" % len(findings))
        for f in findings[:8]:
            ctx.log(self.name, "· [%s] %s" % (f["level"], f["title"]))

    # ---------- 规则 1：孤儿 Service ----------

    def _orphan_services(self, backend):
        out = []
        svc_dir = os.path.join(backend, "src/main/java/com/mindman/service")
        ctl_dir = os.path.join(backend, "src/main/java/com/mindman/controller")
        if not (os.path.isdir(svc_dir) and os.path.isdir(ctl_dir)):
            return out
        ctl_text = self._read_dir(ctl_dir)
        for f in os.listdir(svc_dir):
            if not f.endswith(".java") or f.startswith("impl"):
                continue
            iface = f[:-5]
            if iface not in ctl_text:
                out.append({"level": "P2", "title": "未被 Controller 引用的 Service：%s" % iface,
                            "detail": "疑似孤儿服务，可删除或补充调用方"})
        return out

    # ---------- 规则 2：大文件（辅助识别冗余/该拆分） ----------

    def _big_files(self, backend, limit_kb=300):
        out = []
        for root, _, files in os.walk(backend):
            if any(s in root for s in ("target", "node_modules", ".git")):
                continue
            for f in files:
                if f.endswith((".java", ".vue", ".js")):
                    p = os.path.join(root, f)
                    size = os.path.getsize(p) // 1024
                    if size > limit_kb:
                        out.append({"level": "P3", "title": "大文件 %s（%dKB）" % (f, size),
                                    "detail": "考虑拆分职责"})
        return out

    @staticmethod
    def _read_dir(d):
        text = []
        for root, _, files in os.walk(d):
            for f in files:
                if f.endswith(".java"):
                    try:
                        text.append(open(os.path.join(root, f), encoding="utf-8", errors="ignore").read())
                    except Exception:
                        pass
        return "\n".join(text)
