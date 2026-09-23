#!/usr/bin/env python3
"""Serve the design prototype for phone testing (iOS Simulator or a real device).

`valuelens-preview.html` is an artifact *fragment*: no doctype, no <head>, no viewport meta,
because the artifact platform wraps it in a skeleton that supplies those. Served raw over HTTP a
browser then falls back to a 980px viewport and scales the whole page down, which makes any judgement
about type size or touch targets meaningless. This wraps the fragment in the same skeleton the
artifact platform would, and serves it.

    python3 serve.py [port]            # default 8787

Then in the iOS Simulator's Safari open  http://localhost:<port>/
(the simulator shares the host's network, so localhost reaches this server).
Add to Home Screen to see it without Safari's chrome.
"""
import http.server, pathlib, socketserver, sys

SRC = pathlib.Path(__file__).with_name("valuelens-preview.html")
SKELETON = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-status-bar-style" content="default">
<style>
  :root {{ padding-top: env(safe-area-inset-top, 0px); padding-bottom: env(safe-area-inset-bottom, 0px); }}
  body {{ margin: 0; font: 14px/1.4 system-ui, sans-serif; background: #FAF9F5; }}
  img {{ max-width: 100%; }}
  [hidden] {{ display: none !important; }}
</style>
{fragment}
</head>
<body>
</body>
</html>"""


def page() -> bytes:
    # The fragment opens with <title>/<link>/<style> then drops into markup, so the split point is
    # the first element that belongs in the body.
    text = SRC.read_text()
    cut = text.index('<div class="frame">')
    head, body = text[:cut], text[cut:]
    html = SKELETON.format(fragment=head).replace("<body>\n</body>", f"<body>\n{body}\n</body>")
    return html.encode()


class Handler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        if self.path.split("?")[0] not in ("/", "/index.html", "/valuelens-preview.html"):
            return super().do_GET()
        out = page()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(out)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(out)

    def log_message(self, *a):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8787
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("127.0.0.1", port), Handler) as srv:
        print(f"ValueLens prototype on http://localhost:{port}/  (Ctrl-C to stop)")
        srv.serve_forever()
