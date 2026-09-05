# -*- coding: utf-8 -*-
"""
MindMan 静态预览服务器（生产构建 + gzip + /api 代理）
用法: python serve_dist.py [端口]
默认端口 5173，服务 dist/ 目录，/api 转发到 localhost:8080
"""
import http.server
import socketserver
import gzip
import os
import sys
import urllib.request
import io

DIST_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'code', 'ai-vue', 'dist')
API_TARGET = 'http://localhost:8080'
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 5173

COMPRESS_TYPES = {'.js', '.css', '.html', '.json', '.svg', '.txt', '.md'}


class GzipHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIST_DIR, **kwargs)

    def do_GET(self):
        # /api 代理到后端
        if self.path.startswith('/api/'):
            self._proxy_api()
            return
        super().do_GET()

    def do_POST(self):
        if self.path.startswith('/api/'):
            self._proxy_api()
            return
        self.send_error(404)

    def do_PUT(self):
        if self.path.startswith('/api/'):
            self._proxy_api()
            return
        self.send_error(404)

    def do_DELETE(self):
        if self.path.startswith('/api/'):
            self._proxy_api()
            return
        self.send_error(404)

    def _proxy_api(self):
        try:
            length = int(self.headers.get('Content-Length', 0))
            body = self.rfile.read(length) if length > 0 else None
            req = urllib.request.Request(
                API_TARGET + self.path,
                data=body,
                method=self.command,
                headers={k: v for k, v in self.headers.items() if k.lower() not in ('host', 'connection')}
            )
            resp = urllib.request.urlopen(req, timeout=120)
            data = resp.read()
            self.send_response(resp.status)
            ct = resp.headers.get('Content-Type', 'application/json')
            self.send_header('Content-Type', ct)
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        except urllib.error.HTTPError as e:
            data = e.read()
            self.send_response(e.code)
            self.send_header('Content-Type', e.headers.get('Content-Type', 'application/json'))
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        except Exception as e:
            self.send_response(502)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(('{"code":502,"message":"proxy error: %s"}' % e).encode())

    def send_head(self):
        path = self.translate_path(self.path)
        if os.path.isdir(path):
            # 目录 → index.html
            path = os.path.join(path, 'index.html')
            if not os.path.exists(path):
                path = os.path.join(DIST_DIR, 'index.html')
        if not os.path.exists(path):
            # SPA fallback
            path = os.path.join(DIST_DIR, 'index.html')
        ext = os.path.splitext(path)[1].lower()
        try:
            with open(path, 'rb') as f:
                data = f.read()
        except Exception:
            self.send_error(404)
            return None

        self.send_response(200)
        self.send_header('Content-Type', self.guess_type(path))
        # gzip 压缩
        if ext in COMPRESS_TYPES and len(data) > 512:
            gz = gzip.compress(data, 6)
            if len(gz) < len(data):
                self.send_header('Content-Encoding', 'gzip')
                self.send_header('Content-Length', str(len(gz)))
                self.send_header('Vary', 'Accept-Encoding')
                self.end_headers()
                return io.BytesIO(gz)
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        return io.BytesIO(data)

    def log_message(self, fmt, *args):
        sys.stdout.write('[%s] %s\n' % (self.log_date_time_string(), fmt % args))


class ThreadedServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    allow_reuse_address = True


if __name__ == '__main__':
    if not os.path.exists(DIST_DIR):
        print('dist 目录不存在，请先运行: vite build')
        sys.exit(1)
    print(f'Serving {os.path.abspath(DIST_DIR)} on :{PORT} (gzip on, /api -> {API_TARGET})')
    ThreadedServer(('0.0.0.0', PORT), GzipHandler).serve_forever()
