# Runbook — running the alpha locally

## 1. Engine
```bash
cd services/valuation-engine
python3 -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
cp .env.example .env   # add FRED_API_KEY if you have one
uvicorn valuation_engine.main:app --reload --port 8000
```
Sanity: `curl -H "X-SEC-User-Agent: Your Name you@example.com" localhost:8000/companies/KO/valuation | head -c 400`

Tests: `pytest -q`

## 2. iOS (simulator)
```bash
cd apps/ios
xcodegen generate            # regenerates ValueLens.xcodeproj from project.yml
open ValueLens.xcodeproj     # ⌘R on any iPhone simulator
```
Or headless:
```bash
xcodebuild -project ValueLens.xcodeproj -scheme ValueLens \
  -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
  -derivedDataPath build/DerivedData build
xcrun simctl install booted build/DerivedData/Build/Products/Debug-iphonesimulator/ValueLens.app
xcrun simctl launch booted com.swcsoftware.valuelens
```
Do **not** pass `CODE_SIGNING_ALLOWED=NO` — Keychain writes fail on unsigned builds and the
identity won't persist (ISSUES #13).

The simulator reaches the engine at `http://127.0.0.1:8000` (default in Settings). A physical
device needs your Mac's LAN IP or a hosted engine (PATH_TO_ANY_TICKER.md).

Exports land in the app's Documents folder (visible in Files → On My iPhone → ValueLens).

## 3. Android
Open `apps/android` in Android Studio; it will offer to create the Gradle wrapper. Emulator
reaches the engine at `http://10.0.2.2:8000`.

## 4. Git flow
- Work on `dev`. Commit freely.
- When the simulator build is green and the docs are current: `git checkout staging && git merge --ff-only dev && git push`.
- `main` is owner-only.

## 5. Refreshing bundled sample data
```bash
for t in AAPL KO MSFT; do
  curl -s -H "X-SEC-User-Agent: Dev dev@example.com" "localhost:8000/companies/$t/valuation" \
    > apps/ios/ValueLens/Resources/SampleData/$t.json
done
```
