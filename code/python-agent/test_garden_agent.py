"""Safety checks for evidence-backed garden reflections."""

import json
import os
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from unittest.mock import patch

from garden_agent import call_model, review_record


RECORD = {
    "emotion": "焦虑",
    "content": "今天开会时担心自己讲不清楚，后来同事帮我补充了。",
    "trigger": "工作",
}


class GardenAgentTest(unittest.TestCase):
    def test_calls_an_openai_compatible_model_endpoint(self):
        captured = {}

        class ModelHandler(BaseHTTPRequestHandler):
            def do_POST(self):
                captured["path"] = self.path
                captured["body"] = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                result = {"choices": [{"message": {"content": '{"ok":true}'}}]}
                body = json.dumps(result).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

            def log_message(self, *_):
                pass

        server = HTTPServer(("127.0.0.1", 0), ModelHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with patch.dict(os.environ, {
                "MINDMAN_PY_MODEL_BASE_URL": f"http://127.0.0.1:{server.server_port}/v1",
                "MINDMAN_PY_MODEL": "test-model",
                "MINDMAN_PY_MODEL_TIMEOUT_SECONDS": "2",
            }):
                self.assertEqual(call_model(RECORD), '{"ok":true}')
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)
        self.assertEqual(captured["path"], "/v1/chat/completions")
        self.assertEqual(captured["body"]["model"], "test-model")
        self.assertIn(RECORD["content"], captured["body"]["messages"][1]["content"])

    def test_keeps_direct_evidence_and_discards_unsupported_sleep_score(self):
        reply = {
            "evidence": "今天开会时担心自己讲不清楚",
            "observation": "你写到开会时担心表达不清，也写到同事后来补充了。",
            "question": "同事补充之后，你的感受有什么变化？",
            "scores": {
                "emotion": {"score": 2, "evidence": "焦虑", "reason": "你选择焦虑作为今天的心情"},
                "sleep": {"score": 4, "evidence": "同事帮我补充了", "reason": "推测睡得不错"},
                "stress": {"score": None, "evidence": "", "reason": ""},
            },
        }
        result = review_record(RECORD, model_call=lambda _: json.dumps(reply, ensure_ascii=False))
        self.assertEqual(result["source"], "agent")
        self.assertEqual(result["emotionScore"]["score"], 2)
        self.assertIsNone(result["sleepScore"])

    def test_rejects_a_quote_absent_from_the_record(self):
        reply = {
            "evidence": "我被大家批评了",
            "observation": "你被批评了。",
            "question": "后来呢？",
        }
        result = review_record(RECORD, model_call=lambda _: json.dumps(reply, ensure_ascii=False))
        self.assertEqual(result["source"], "unavailable")

    def test_model_failure_stays_available_to_java_fallback(self):
        def fail(_):
            raise OSError("model offline")

        result = review_record(RECORD, model_call=fail)
        self.assertEqual(result["source"], "unavailable")


if __name__ == "__main__":
    unittest.main()
