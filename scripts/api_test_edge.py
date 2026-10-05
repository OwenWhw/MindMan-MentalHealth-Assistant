#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
MindMan 接口测试 · 边界与安全补充用例（v2，关键字传参避免顺序错误）

覆盖 4 类场景：
  1. 鉴权越权：访问/操作不属于自己的资源、无 token 探测
  2. 参数边界：空值、非法枚举、超长、负分页、越界分页、类型错误、畸形 JSON
  3. 业务约束：重复归档、归档后发消息、重复注册
  4. 安全：SQL 注入、堆栈泄露、XSS 存储、普通用户越权

用法：python scripts/api_test_edge.py [--base http://localhost:8080]
"""

import argparse
import json
import ssl
import time
import urllib.error
import urllib.request

SSL_CTX = ssl.create_default_context()
SSL_CTX.check_hostname = False
SSL_CTX.verify_mode = ssl.CERT_NONE

RESULTS = []


def call(base, method, path, body=None, token=None, timeout=60, raw_body=None):
    url = base.rstrip("/") + path
    data = raw_body if raw_body is not None else (json.dumps(body).encode("utf-8") if body is not None else None)
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    start = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=SSL_CTX) as resp:
            text, status = resp.read().decode("utf-8", "ignore"), resp.status
    except urllib.error.HTTPError as e:
        text, status = e.read().decode("utf-8", "ignore"), e.code
    except Exception as e:
        text, status = str(e), 0
    ms = int((time.time() - start) * 1000)
    try:
        return status, json.loads(text), ms
    except Exception:
        return status, text, ms


def record(tid, name, expect, actual, passed, note="", severity="一般"):
    """passed 必须是布尔；severity 用于区分'缺陷'与'待产品确认'"""
    tag = "PASS" if passed else ("BUG" if severity == "缺陷" else "WARN")
    RESULTS.append({"id": tid, "name": name, "expect": expect, "actual": actual,
                    "result": tag, "note": note, "severity": severity})
    print("[%s] %-36s 期望:%-24s 实际:%s%s" % (tag, name, expect, actual, ("  ← " + note) if note else ""))
    return passed


def login(base, username, password):
    st, d, _ = call(base, "POST", "/api/auth/login", {"username": username, "password": password})
    if isinstance(d, dict) and d.get("code") == 200:
        return d["data"]["token"]
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--report", default="")
    args = ap.parse_args()
    base = args.base

    print("=" * 88)
    print("MindMan 接口测试 · 边界与安全用例   base=%s" % base)
    print("=" * 88)

    admin_token = login(base, "admin", "123456")
    if not admin_token:
        print("!! 管理员登录失败，终止")
        return

    # ============ 1. 鉴权与越权 ============
    print("\n【1. 鉴权与越权】")
    _, d, _ = call(base, "GET", "/api/chat/sessions/999999/messages", token=admin_token)
    code = d.get("code") if isinstance(d, dict) else None
    record("E-AUTH-01", "访问不存在的会话消息", "拒绝(业务码 404)",
           "code=%s" % code, code in (404, 403))

    _, d, _ = call(base, "DELETE", "/api/chat/sessions/999999", token=admin_token)
    code = d.get("code") if isinstance(d, dict) else None
    record("E-AUTH-02", "删除不存在的会话", "拒绝(业务码 404)",
           "code=%s" % code, code in (404, 403))

    protected = ["/api/emotion/garden", "/api/knowledge/article/page?page=1&pageSize=1",
                 "/api/chat/sessions", "/api/admin/users?page=1&pageSize=1",
                 "/api/admin/prompt/list", "/api/admin/rag/status"]
    bad = []
    for p in protected:
        st, _, _ = call(base, "GET", p)
        if st != 401:
            bad.append("%s->%s" % (p, st))
    record("E-AUTH-03", "6 个受保护接口无 token 均拦截", "全部 HTTP 401",
           "401 %d/6" % (len(protected) - len(bad)), not bad, ";".join(bad))

    # ============ 2. 参数边界 ============
    print("\n【2. 参数边界与非法输入】")
    _, d, _ = call(base, "POST", "/api/emotion/garden", {"emotion": "", "content": "x"}, token=admin_token)
    code = d.get("code") if isinstance(d, dict) else None
    record("E-PARAM-01", "情绪记录 emotion 传空串", "校验失败(400)",
           "code=%s" % code, code == 400)

    _, boundary_session, _ = call(base, "POST", "/api/chat/sessions", {}, token=admin_token)
    boundary_session_id = None
    if isinstance(boundary_session, dict) and boundary_session.get("code") == 200:
        created = boundary_session.get("data")
        boundary_session_id = created.get("id") if isinstance(created, dict) else created

    _, d, _ = call(base, "POST", "/api/emotion/garden",
                   {"emotion": "不存在的情绪类型", "content": "x"}, token=admin_token)
    invalid_flower_id = d.get("data", {}).get("flowerId") if isinstance(d, dict) and isinstance(d.get("data"), dict) else None
    code = d.get("code") if isinstance(d, dict) else None
    record("E-PARAM-02", "情绪记录传非法枚举值", "应有枚举白名单校验",
           "code=%s（被接受并入库）" % code, code != 200,
           severity="缺陷", note="无枚举校验，脏数据可入库（建议用 @Pattern 或枚举校验）")

    _, d, _ = call(base, "POST", "/api/chat/messages",
                   {"sessionId": boundary_session_id, "content": "焦虑" * 1200}, token=admin_token) if boundary_session_id else (0, {}, 0)
    code = d.get("code") if isinstance(d, dict) else None
    record("E-PARAM-03", "对话内容超长(2400字 > 2000上限)", "校验失败(400)",
           "code=%s" % code, code == 400 and boundary_session_id is not None)
    if boundary_session_id:
        st, result, _ = call(base, "DELETE", "/api/chat/sessions/%s" % boundary_session_id, token=admin_token)
        print("  [teardown] 边界测试会话 id=%s: %s" % (boundary_session_id, "已删除" if st == 200 and isinstance(result, dict) and result.get("code") == 200 else "删除失败"))

    _, d, _ = call(base, "GET", "/api/knowledge/article/page?page=-1&pageSize=-5", token=admin_token)
    code = d.get("code") if isinstance(d, dict) else None
    record("E-PARAM-04", "分页参数传负数", "应纠正为默认值或返回空",
           "code=%s" % code, code == 200,
           severity="待确认", note="未报错，需确认是否做了兜底（否则 SQL LIMIT 负数可能异常）")

    _, d, _ = call(base, "GET", "/api/knowledge/article/page?page=99999&pageSize=10", token=admin_token)
    lst = (d.get("data") or {}).get("list") if isinstance(d, dict) else None
    record("E-PARAM-05", "分页越界(page=99999)", "返回空列表不报错",
           "code=%s, list=%s" % (d.get("code"), len(lst) if lst is not None else "?"),
           isinstance(lst, list) and len(lst) == 0)

    st, d, _ = call(base, "GET", "/api/knowledge/article/abc", token=admin_token)
    code = d.get("code") if isinstance(d, dict) else None
    record("E-PARAM-06", "路径参数类型错误(article/abc)", "400 而非 500",
           "HTTP=%s code=%s" % (st, code), st == 400 or code == 400)

    st, _, _ = call(base, "POST", "/api/auth/login", raw_body=b'{bad json')
    record("E-PARAM-07", "请求体畸形 JSON", "400 而非 500", "HTTP=%s" % st, st == 400)

    # ============ 3. 业务约束 ============
    print("\n【3. 业务约束】")
    _, d, _ = call(base, "POST", "/api/chat/sessions", {}, token=admin_token)
    sid = None
    if isinstance(d, dict) and d.get("code") == 200:
        dd = d.get("data")
        sid = dd.get("id") if isinstance(dd, dict) else dd

    if sid:
        _, d1, _ = call(base, "PUT", "/api/chat/sessions/%s/archive" % sid, token=admin_token)
        _, d2, _ = call(base, "PUT", "/api/chat/sessions/%s/archive" % sid, token=admin_token)
        c1 = d1.get("code") if isinstance(d1, dict) else None
        c2 = d2.get("code") if isinstance(d2, dict) else None
        record("E-BIZ-01", "同一会话重复归档", "第二次幂等(200)或明确拒绝(400)",
               "第一次=%s 第二次=%s" % (c1, c2), c2 in (200, 400),
               severity="待确认", note="当前为幂等处理" if c2 == 200 else "第二次被拒")

        _, d, _ = call(base, "POST", "/api/chat/messages",
                       {"sessionId": sid, "content": "归档后还能聊吗"}, token=admin_token, timeout=180)
        code = d.get("code") if isinstance(d, dict) else None
        record("E-BIZ-02", "向已归档会话继续发消息", "拒绝 或 自动重开为进行中",
               "code=%s（仍可对话）" % code, code != 200,
               severity="待确认", note="归档后仍可发消息，需产品确认语义")
        st, result, _ = call(base, "DELETE", "/api/chat/sessions/%s" % sid, token=admin_token)
        print("  [teardown] 测试会话 id=%s: %s" % (sid, "已删除" if st == 200 and isinstance(result, dict) and result.get("code") == 200 else "删除失败"))

    uname = "edgetest_%d" % int(time.time())
    phone = "13" + str(int(time.time()))[-9:]
    _, d, _ = call(base, "POST", "/api/auth/register",
                   {"username": uname, "password": "test1234", "nickname": "边界测试", "phone": phone})
    test_user_id = d.get("data", {}).get("userId") if isinstance(d, dict) and isinstance(d.get("data"), dict) else None
    c1 = d.get("code") if isinstance(d, dict) else None
    _, d, _ = call(base, "POST", "/api/auth/register",
                   {"username": uname, "password": "test1234", "nickname": "边界测试", "phone": phone})
    c2 = d.get("code") if isinstance(d, dict) else None
    record("E-BIZ-03", "重复用户名注册", "第一次成功、第二次拒绝",
           "第一次=%s 第二次=%s" % (c1, c2), c1 == 201 and c2 != 201)

    # ============ 4. 安全 ============
    print("\n【4. 安全用例】")
    _, d, _ = call(base, "POST", "/api/auth/login",
                   {"username": "admin' OR '1'='1", "password": "x' OR '1'='1"})
    code = d.get("code") if isinstance(d, dict) else None
    record("E-SEC-01", "登录 SQL 注入试探", "登录失败(非 200)",
           "code=%s" % code, code != 200)

    _, d, _ = call(base, "GET", "/api/emotion/garden", token=admin_token)
    body = json.dumps(d, ensure_ascii=False)
    record("E-SEC-02", "响应未泄露堆栈/内部路径", "无 Exception/StackTrace 关键字",
           "含关键字=%s" % any(k in body for k in ("Exception", "StackTrace", "java.")),
           not any(k in body for k in ("Exception", "StackTrace", "java.")))

    xss = "<script>alert('xss')</script>"
    _, d, _ = call(base, "POST", "/api/emotion/garden",
                   {"emotion": "平静", "content": xss}, token=admin_token)
    xss_flower_id = d.get("data", {}).get("flowerId") if isinstance(d, dict) and isinstance(d.get("data"), dict) else None
    code = d.get("code") if isinstance(d, dict) else None
    _, d2, _ = call(base, "GET", "/api/emotion/garden", token=admin_token)
    stored = xss in json.dumps(d2, ensure_ascii=False)
    record("E-SEC-03", "XSS 载荷写入并回显", "后端原样存储(前端须转义渲染)",
           "写入=%s 回显原文=%s" % (code, stored), code == 200,
           severity="待确认", note="需人工确认前端用文本渲染（禁止 v-html）")

    uname2 = "edgeuser_%d" % int(time.time())
    phone2 = "13" + str(int(time.time()))[-8:] + "7"
    _, d, _ = call(base, "POST", "/api/auth/register",
                   {"username": uname2, "password": "test1234", "nickname": "普通用户", "phone": phone2})
    second_user_id = d.get("data", {}).get("userId") if isinstance(d, dict) and isinstance(d.get("data"), dict) else None
    tok2 = login(base, uname2, "test1234")
    if tok2:
        hijacked = []
        for p in ["/api/admin/users?page=1&pageSize=1", "/api/admin/prompt/list",
                  "/api/admin/rag/status", "/api/admin/crawler/seeds"]:
            _, dd, _ = call(base, "GET", p, token=tok2)
            cc = dd.get("code") if isinstance(dd, dict) else None
            if cc == 200:
                hijacked.append(p)
        record("E-SEC-04", "普通用户越权访问 4 个管理接口", "全部拒绝",
               "越权成功 %d/4 %s" % (len(hijacked), hijacked), not hijacked,
               severity="缺陷", note="越权成功=高危漏洞" if hijacked else "")
    else:
        record("E-SEC-04", "普通用户越权访问管理接口", "全部拒绝", "普通用户登录失败，跳过",
               False, severity="待确认")

    for flower_id in (invalid_flower_id, xss_flower_id):
        if flower_id:
            st, result, _ = call(base, "DELETE", "/api/emotion/garden/%s" % flower_id, token=admin_token)
            print("  [teardown] 测试情绪记录 id=%s: %s" % (flower_id, "已删除" if st == 200 and isinstance(result, dict) and result.get("code") == 200 else "删除失败"))
    for user_id in (test_user_id, second_user_id):
        if user_id:
            st, result, _ = call(base, "DELETE", "/api/admin/users/%s" % user_id, token=admin_token)
            print("  [teardown] 测试账号 id=%s: %s" % (user_id, "已删除" if st == 200 and isinstance(result, dict) and result.get("code") == 200 else "删除失败"))

    # ============ 汇总 ============
    p = sum(1 for r in RESULTS if r["result"] == "PASS")
    bugs = [r for r in RESULTS if r["result"] == "BUG"]
    warns = [r for r in RESULTS if r["result"] == "WARN"]
    print("\n" + "=" * 88)
    print("汇总：共 %d 条 → 通过 %d，确认缺陷 %d，待确认 %d" % (len(RESULTS), p, len(bugs), len(warns)))
    print("=" * 88)
    for r in bugs + warns:
        print("  [%s] %s %s：%s %s" % (r["result"], r["id"], r["name"], r["actual"], r["note"]))

    if args.report:
        json.dump({"base": base, "time": time.strftime("%Y-%m-%d %H:%M:%S"),
                   "total": len(RESULTS), "passed": p, "bugs": len(bugs), "warns": len(warns),
                   "cases": RESULTS},
                  open(args.report, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
        print("\n结果已写入 %s" % args.report)


if __name__ == "__main__":
    main()
