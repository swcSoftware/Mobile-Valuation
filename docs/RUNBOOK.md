# Runbook — running the alpha locally

## 1. Engine
```bash
cd services/valuation-engine
python3 -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
cp .env.example .env   # then put FRED_API_KEY / POLYGON_API_KEY in .env (git-ignored — never in .env.example)
uvicorn valuation_engine.main:app --reload --port 8000
```
For a physical phone on the same Wi-Fi: `scripts/serve-lan.sh` (binds 0.0.0.0 and prints the URLs;
`/health` also lists them and the apps' Settings offer them as one-tap options).
Sanity: `curl -H "X-SEC-User-Agent: Your Name you@example.com" localhost:8000/companies/KO/valuation | head -c 400`

Tests: `pytest -q` (26 tests, offline; fixtures regenerated with `tests/fixtures/make_fixture.py`)

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
```bash
cd apps/android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # or any JDK 17+
./gradlew assembleDebug testDebugUnitTest
~/Library/Android/sdk/emulator/emulator -avd Pixel_10 &
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
~/Library/Android/sdk/platform-tools/adb shell am start -n com.swcsoftware.valuelens/.MainActivity
# deep link: adb shell am start -a android.intent.action.VIEW -d "valuelens://ticker/KO"
```
Or open `apps/android` in Android Studio. Emulator reaches the engine at `http://10.0.2.2:8000`.
Exports land in `Android/data/com.swcsoftware.valuelens/files/Documents/ValueLens/`.

## 3b. All automated tests
```bash
(cd services/valuation-engine && .venv/bin/python -m pytest -q)
(cd apps/ios && xcodebuild -project ValueLens.xcodeproj -scheme ValueLens -destination 'platform=iOS Simulator,name=iPhone 16 Pro' test)
(cd apps/android && ./gradlew testDebugUnitTest)
```

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
cp apps/ios/ValueLens/Resources/SampleData/*.json apps/android/app/src/main/assets/
```
