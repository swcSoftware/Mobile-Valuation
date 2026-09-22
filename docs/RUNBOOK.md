# Runbook — running the alpha locally

> Environment gotchas (simulator UDID, JAVA_HOME, `--rerun`, signing) are in [../CLAUDE.md](../CLAUDE.md).

Since Sprint 2 the apps need **no server**: they call SEC EDGAR, the quote feed and the published
rates file directly. The Python engine (§1) is only for development and regenerating oracle files.

## 0. Shared core (needed by both apps)
```bash
cd apps/android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # or any JDK 17+
./gradlew :valuation-core:desktopTest                                   # 12 tests incl. Python oracle diff
./gradlew :valuation-core:assembleValuationCoreReleaseXCFramework       # iOS framework (Xcode pre-build does this if missing)
```

## 1. Engine
```bash
cd services/valuation-engine
python3 -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"
cp .env.example .env   # then put FRED_API_KEY / POLYGON_API_KEY in .env (git-ignored — never in .env.example)
uvicorn valuation_engine.main:app --reload --port 8000
```
(`scripts/serve-lan.sh`, Dockerfile and fly.toml remain for reference; the apps no longer use an engine.)
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

The first build runs Gradle to produce `ValuationCore.xcframework` (needs JAVA_HOME or Android Studio).

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
Or open `apps/android` in Android Studio.
Exports land in `Android/data/com.swcsoftware.valuelens/files/Documents/ValueLens/`.

## 3b. All automated tests (60)
```bash
(cd services/valuation-engine && .venv/bin/python -m pytest -q)                      # 26
(cd apps/android && ./gradlew :valuation-core:desktopTest testDebugUnitTest)          # 12 + 7
(cd apps/ios && xcodebuild -project ValueLens.xcodeproj -scheme ValueLens -destination 'platform=iOS Simulator,name=iPhone 16 Pro' test)  # 15
```

## 3c. Published data files (GitHub Pages)
Owner setup once: repo secret `FRED_API_KEY`; Settings → Pages → branch `gh-pages`; Actions → "Publish data files" → Run.
Local dry run:
```bash
FRED_API_KEY=… services/valuation-engine/.venv/bin/python scripts/publish_rates.py /tmp/site
SEC_USER_AGENT="Name email" services/valuation-engine/.venv/bin/python scripts/publish_tickers.py /tmp/site
```

## 4. Git flow
- Work on `dev`. Commit freely.
- When the simulator build is green and the docs are current: `git checkout staging && git merge --ff-only dev && git push`.
- `main` is owner-only.

## 5. Refreshing bundled sample data (through the core, with checks and beta)
```bash
cd apps/android
VL_DUMP_DIR=/tmp/samples SEC_USER_AGENT="Name email" ./gradlew :valuation-core:desktopTest --tests '*SampleDump*'
cp /tmp/samples/*.json ../ios/ValueLens/Resources/SampleData/
cp /tmp/site/rates.json ../ios/ValueLens/Resources/SampleData/rates.json     # from §3c
cp ../ios/ValueLens/Resources/SampleData/*.json app/src/main/assets/
```
