# Candidate pack

Read `ASSESSMENT.md` first. All files in `fixtures/` are fictional. The five source streams have intentionally different representations; `access_scopes.json` is a sixth support fixture for role checks. `SHA256SUMS.json` describes the baseline bytes, excluding itself.

You can ingest the files directly or run the supplied source stand-in:

```bash
python3 serve_synthetic_portals.py
curl -H 'X-Demo-Token: synthetic-read-only-token' http://127.0.0.1:8765/dp/positions
```

The local source requires a demo token for each read. That token protects no real information and **is not an authentication design** for your application. Your app must enforce the scope and roles from `access_scopes.json` on its own API. Synthetic reports intentionally mix an older cut, a pending movement, reused display symbol, duplicate bank reference, and an event effective after the report cut. There may be several valid case modeling choices if evidence stays traceable.

You are not expected to reproduce a live exchange schema byte for byte; explain which actual public files you obtained and how you would map their fields. Offline review must run without access to exchange websites.
