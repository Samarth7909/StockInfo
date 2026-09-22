"""Read-only stand-in for several private source portals. Contains fictional data."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse


FIXTURES = Path(__file__).resolve().parent / "fixtures"
ROUTES = {
    "/broker/holdings": ("internal_holdings.csv", "text/csv; charset=utf-8"),
    "/dp/positions": ("dp_positions.html", "text/html; charset=utf-8"),
    "/broker/ledger": ("ledger_events.jsonl", "application/x-ndjson"),
    "/bank/confirmation": ("bank_confirmation.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    "/exchange/reference": ("exchange_reference.csv", "text/csv; charset=utf-8"),
}
TOKEN = "synthetic-read-only-token"


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        route = urlparse(self.path).path
        if route == "/":
            body = ("Synthetic source portals\n" + "\n".join(ROUTES) + "\n").encode()
            return self.respond(200, body, "text/plain; charset=utf-8")
        if route not in ROUTES:
            return self.respond(404, b"unknown source\n", "text/plain")
        if self.headers.get("X-Demo-Token") != TOKEN:
            return self.respond(401, b"demo token required\n", "text/plain")
        name, mime = ROUTES[route]
        data = (FIXTURES / name).read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("X-Source-Report-Cut", "2026-09-15T18:00:00+05:30")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def respond(self, status, body, mime):
        self.send_response(status)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    server = ThreadingHTTPServer(("127.0.0.1", 8765), Handler)
    print("Synthetic, read-only portals at http://127.0.0.1:8765", flush=True)
    server.serve_forever()
