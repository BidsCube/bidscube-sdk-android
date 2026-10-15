#!/usr/bin/env python3
"""Minimal local VAST tracking collector for QA (not production).

Usage:
  python3 tools/local_tracking_collector.py
  adb reverse tcp:8765 tcp:8765

Creatives should ping http://127.0.0.1:8765/t?e=<event>
"""

from __future__ import annotations

import json
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs, urlparse

PORT = 8765
EVENTS: list[dict] = []


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt: str, *args) -> None:
        # quieter default access log; we print structured events instead
        pass

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path in ("/t", "/track"):
            qs = parse_qs(parsed.query)
            event = (qs.get("e") or qs.get("event") or ["unknown"])[0]
            row = {
                "ts": time.time(),
                "event": event,
                "path": self.path,
                "ua": self.headers.get("User-Agent", ""),
            }
            EVENTS.append(row)
            print(f"COLLECT {event} path={self.path}", flush=True)
            body = b"ok\n"
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Connection", "close")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(body)
            return
        if parsed.path == "/dump":
            body = json.dumps(EVENTS, indent=2).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return
        if parsed.path == "/reset":
            EVENTS.clear()
            self.send_response(204)
            self.end_headers()
            return
        self.send_response(404)
        self.end_headers()


if __name__ == "__main__":
    server = HTTPServer(("127.0.0.1", PORT), Handler)
    print(f"Local tracking collector on http://127.0.0.1:{PORT}/t?e=…", flush=True)
    print("adb reverse tcp:8765 tcp:8765", flush=True)
    server.serve_forever()
