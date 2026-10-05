"""Ollama / OpenAI 兼容 LLM 客户端（零第三方依赖，仅用标准库）。

设计原则：
- LLM 只用于「模糊环节」（生成用例、失败归因、摘要），执行环节不依赖 LLM
- 所有调用失败都不抛异常打断流水线，而是返回降级结果（fallback）
- 强制 JSON 输出 + 文本中提取 JSON 的双保险，规避小模型输出不稳定
"""

import json
import re
import ssl
import urllib.request

SSL_CTX = ssl.create_default_context()
SSL_CTX.check_hostname = False
SSL_CTX.verify_mode = ssl.CERT_NONE


class LLMClient:
    def __init__(self, cfg):
        self.cfg = cfg or {}
        self.enabled = bool(self.cfg.get("enabled", True))
        self.base_url = (self.cfg.get("baseUrl") or "http://localhost:11434/v1").rstrip("/")
        self.model = self.cfg.get("model", "qwen2.5:7b")
        self.timeout = int(self.cfg.get("timeout", 180))
        self.temperature = float(self.cfg.get("temperature", 0.2))

    # ---------- 基础调用 ----------

    def chat(self, system, user, json_mode=False):
        """单轮对话。失败返回 None（调用方负责降级）。"""
        if not self.enabled:
            return None
        body = {
            "model": self.model,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
            "temperature": self.temperature,
            "stream": False,
        }
        if json_mode:
            body["response_format"] = {"type": "json_object"}
        req = urllib.request.Request(
            self.base_url + "/chat/completions",
            data=json.dumps(body).encode("utf-8"),
            method="POST",
        )
        req.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(req, timeout=self.timeout, context=SSL_CTX) as resp:
                data = json.loads(resp.read().decode("utf-8", "ignore"))
            return (data.get("choices") or [{}])[0].get("message", {}).get("content", "").strip()
        except Exception as e:  # 网络/模型不可用 → 降级
            print("      [llm] 调用失败，降级: %s" % str(e)[:120])
            return None

    def chat_json(self, system, user, fallback=None):
        """要求 JSON 返回；解析失败时尝试从文本中抽取 JSON。"""
        raw = self.chat(system, user, json_mode=True)
        if not raw:
            return fallback
        parsed = self._extract_json(raw)
        if parsed is None:
            print("      [llm] JSON 解析失败，使用降级方案")
            return fallback
        return parsed

    @staticmethod
    def _extract_json(text):
        text = text.strip()
        # 去掉 markdown 代码围栏
        if text.startswith("```"):
            text = re.sub(r"^```[a-zA-Z]*\n?", "", text)
            text = re.sub(r"\n?```$", "", text).strip()
        try:
            return json.loads(text)
        except Exception:
            pass
        # 兜底：抓第一个 { 或 [ 到最后一个 } 或 ]
        for left, right in (("{", "}"), ("[", "]")):
            i, j = text.find(left), text.rfind(right)
            if i != -1 and j > i:
                try:
                    return json.loads(text[i:j + 1])
                except Exception:
                    continue
        return None
