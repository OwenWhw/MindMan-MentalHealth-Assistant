"""HTTP 工具：统一请求、登录鉴权、结果标准化（标准库实现）。"""

import json
import ssl
import time
import urllib.error
import urllib.request

SSL_CTX = ssl.create_default_context()
SSL_CTX.check_hostname = False
SSL_CTX.verify_mode = ssl.CERT_NONE


def request(base_url, method, path, body=None, token=None, timeout=30, raw_body=None):
    """返回 dict：{status, data, ms, error}"""
    url = base_url.rstrip("/") + path
    data = raw_body if raw_body is not None else (
        json.dumps(body).encode("utf-8") if body is not None else None)
    req = urllib.request.Request(url, data=data, method=method.upper())
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    start = time.time()
    status, error = 0, None
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=SSL_CTX) as resp:
            text, status = resp.read().decode("utf-8", "ignore"), resp.status
    except urllib.error.HTTPError as e:
        text, status = e.read().decode("utf-8", "ignore"), e.code
    except Exception as e:
        text, error = "", str(e)
    ms = int((time.time() - start) * 1000)
    try:
        parsed = json.loads(text)
    except Exception:
        parsed = text
    return {"status": status, "data": parsed, "ms": ms, "error": error}


def login(base_url, auth_cfg):
    """登录并返回 token（失败返回 None）"""
    r = request(base_url, "POST", auth_cfg.get("loginPath", "/api/auth/login"),
                {"username": auth_cfg.get("username"), "password": auth_cfg.get("password")})
    d = r.get("data")
    if isinstance(d, dict) and d.get("code") == 200:
        return d.get("data", {}).get("token")
    return None


def biz_code(result):
    """取业务码（项目约定 HTTP 恒 200，错误码在 body.code）"""
    d = result.get("data")
    if isinstance(d, dict):
        return d.get("code")
    return None


def body_preview(result, limit=160):
    d = result.get("data")
    text = json.dumps(d, ensure_ascii=False) if not isinstance(d, str) else d
    return text[:limit]
