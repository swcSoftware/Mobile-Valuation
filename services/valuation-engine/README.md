# valuation-engine

FastAPI service that turns SEC EDGAR XBRL "companyfacts" into normalized financial statements and
runs the ValueLens valuation models.

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
cp .env.example .env            # optional
uvicorn valuation_engine.main:app --reload --port 8000
open http://127.0.0.1:8000/docs
```

Every request to SEC must carry a compliant `User-Agent`. The iOS/Android clients forward the
user's identity in the `X-SEC-User-Agent` header; the service falls back to `SEC_USER_AGENT`.

See `../../docs/API.md` for the contract and `../../docs/ARCHITECTURE.md` for the module map.
