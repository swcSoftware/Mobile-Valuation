# Working in this repo

ValueLens (the project's working name) ships to users as **Alpha** — App Store name "Alpha - Stock
Valuations", home-screen name "Alpha" (owner decision, 2026-09-24). Every string a user reads says
Alpha; code, folders, the bundle ID `com.swcsoftware.valuelens`, the `valuelens://` link scheme, the
`valuelens:derived` data key, log tags and network identifiers keep the working name, deliberately.
Native iOS + Android value-investing app over SEC EDGAR. One Kotlin Multiplatform core
runs **on the device**; there is no server. Deep context lives in `docs/` — start with
[docs/TASKS.md](docs/TASKS.md) (current sprint) and [docs/SPRINTS.md](docs/SPRINTS.md) (what shipped
when). This file is the index and the tripwires.

**Sprint 5 is closed and on `staging`** (2026-09-24, `d403096`): two layouts over one set of numbers,
with the report card as the default, grading in the core, runtime theming, and first-letter-capital
company names. The next sprint has not been planned yet — see the carry-overs at the end of
[docs/SPRINTS.md](docs/SPRINTS.md). [docs/DESIGN.md](docs/DESIGN.md) records every UI decision and
why; read it before touching presentation code.

## Non-negotiables

1. **Branches.** Work only on `dev`. Merge `dev` → `staging` only when every suite is green and no
   app-breaking bug is open. **Never push to `main`** — owner approval only, and it has never been
   pushed.
2. **No silent numbers.** Every model input is read from a filing, measured from market data, or
   labeled *assumed* in the UI. An unlabeled default is a bug (that's how beta = 1.0 shipped in
   Sprint 1 — see ISSUES #31). Failed data checks withhold the value rather than showing one.
3. **Python is the oracle.** `services/valuation-engine` is the reference implementation for
   normalization and the two general models. Change it **first**, mirror in Kotlin, then
   `cd services/valuation-engine && .venv/bin/python tests/fixtures/make_expected.py`. `OracleTest`
   diffs every number and string; a diff is a real difference, not noise.
4. **Additive, not rewrites.** Working code stays working. A fix for one company or one class of
   filer must not reshape the path every other company takes. In order of preference:

   1. **A trigger the common path never enters** — best. The successor-issuer lookup only runs when
      a CIK has no annual data; share-class resolution only when the share count is missing or
      inconsistent; sector models only for a matching SIC. AAPL never executes any of them.
   2. **A guarded step on the shared path** — acceptable when ordering forces it. The share-scale
      correction (ISSUES #77) must run before derived per-share figures, so it sits in the
      pipeline behind an early exit that returns immediately unless the count is off by >100×.
   3. **Changing existing logic** — last resort, and only with a reason that applies to *every*
      filer, not just the one that prompted it.

   **Rewrite only when** the defect is genuinely global (the freshest-tag bug in #35 was: it
   silently dropped KO's debt and PG's cash), or a demonstrably stronger model replaces a weaker one
   with the owner's agreement (normalized working capital in Sprint 3). Say which it is in the
   commit message.

   **Evidence is part of the fix, not optional:**
   - The oracle diff must be byte-identical for unaffected filers — regenerate only when the
     reference implementation deliberately changed, and say so.
   - Measure the blast radius before and after (`scripts/coverage_probe.py`, or a scan over
     `scripts/universe.txt`): "1 of 77 tickers affected" is a claim you can check, "should be safe"
     is not.
   - Add a test asserting the untouched path is untouched (see `ShareScaleTest.correctlyScaledFilersAreUntouched`).
   - `git show --stat` on the fix should be mostly insertions. Deletions in normalization or model
     code deserve a sentence explaining them.
5. **The tag map never self-updates.** `Concepts.kt` / `tags.py` changes go through human review.
   `ConceptMapDriftTest` asserts the two are identical; `docs/COVERAGE.md` (weekly probe) is the gap
   backlog. Edit both maps together, then regenerate the oracle.
6. **No technical analysis. Ever.** Charts of price, momentum, RSI and friends are permanently out of
   scope (blueprint §1). Charts of *fundamentals* over filed years are fine and already shipped.
7. **A layout never computes a number.** Presentation reads `ValuationReport`, `ModelResult` and
   `ExplainSummary` and renders them. Every number, label and verdict comes from the core, which is
   what keeps two layouts from disagreeing. A grading threshold is a judgement, so it lives in the
   core too, next to the facts, and is printed in the UI so a reader can check it.
8. **No machine key on a value screen.** Anything drawn from a report — a tag, an input key, a
   warning, a check message, a note, a formula — goes through `DisplayLabels` (`Labels` in Swift).
   Raw tags live only on the Index page. New keys need an entry in `display-labels.json`; the test
   tells you which.

## Layout

| Path | What |
|---|---|
| `packages/valuation-core` | Kotlin Multiplatform core: EDGAR, normalization, Model A/B, sector models, share classes, data checks, beta, plain-language copy. Runs inside both apps. |
| `apps/ios` | SwiftUI (iOS 17). Consumes `ValuationCore.xcframework`. Project generated by `xcodegen`. |
| `apps/android` | Jetpack Compose (minSdk 26). Consumes the core as a Gradle module. Also the Gradle entry point for core tasks. |
| `services/valuation-engine` | Python reference + oracle fixtures. Not shipped, not hosted. |
| `scripts`, `.github/workflows` | Publish `rates.json` (FRED) and `tickers.json` to GitHub Pages daily. |
| `docs` | Plan, architecture, API contract, tasks, issues, sprints, runbook, data verification, UI direction. |
| `packages/valuation-core/labels/display-labels.json` | **What the app calls things.** Every SEC tag, model key and formula term shown on screen. Edit freely: it changes words, never numbers. `DisplayLabelsTest` checks it; Xcode and Gradle rebuild on change. |
| `docs/design` | Sprint 5 working files: the share-site prototype and the three explored directions. Not shipped, not built, not tested by CI. |

## Commands that actually work here

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # no system JDK

# All tests (run from repo root)
(cd services/valuation-engine && .venv/bin/python -m pytest -q)                        # 31
(cd apps/android && ./gradlew :valuation-core:desktopTest testDebugUnitTest)           # 41
(cd apps/ios && xcodebuild -project ValueLens.xcodeproj -scheme ValueLens \
   -destination 'platform=iOS Simulator,id=CCCBA21C-24A4-488B-B751-EECB523241B9' \
   -derivedDataPath build/DerivedData -configuration Debug test)                      # 15

# iOS framework (Xcode's pre-build script does this too)
(cd apps/android && ./gradlew :valuation-core:assembleValuationCoreReleaseXCFramework)

# Concept-map coverage probe → docs/COVERAGE.md (weekly in CI; fails on regression)
(cd services/valuation-engine && SEC_USER_AGENT="Name email" .venv/bin/python ../../scripts/coverage_probe.py)

# Live probe / regenerate bundled samples (env vars are NOT Gradle inputs → --rerun)
(cd apps/android && VL_PROBE_TICKERS="XOM,JPM,BRK-B" SEC_USER_AGENT="Name email" \
   ./gradlew :valuation-core:desktopTest --tests '*SampleDump*' --rerun -i | grep PROBE)

# Dump full report JSON for arbitrary tickers (design work, share-site data)
(cd apps/android && VL_DUMP_DIR=/tmp/ui VL_DUMP_TICKERS="AAPL,MCD,BRK-B,AGNC,CRWV,O,PLTR" \
   SEC_USER_AGENT="Name email" ./gradlew :valuation-core:desktopTest --tests '*SampleDump*' --rerun)
```

## Tripwires (each one cost a debugging cycle)

- **`-destination 'name=iPhone 16 Pro'` fails** on this Xcode; use the UDID above.
- **Never build iOS with `CODE_SIGNING_ALLOWED=NO`** — Keychain writes fail silently and the SEC
  identity won't persist (ISSUES #13).
- **JVM-only Kotlin APIs** (`toSortedSet`, `String.format`) compile for desktop/Android and break
  only on `iosArm64`. "The core compiles" means nothing until the XCFramework builds.
- **Kotlin → Swift**: every facade method needs `@Throws` or an exception aborts the app; the
  `Fetcher` port returns `FetchResult` because Swift cannot throw Kotlin exceptions; map errors with
  `ValuationCore.errorCode`.
- **Never set `Accept-Encoding: gzip` by hand** — OkHttp/URLSession then hand back compressed bytes.
- **iOS must pass the core's `rawJSON` back** for `explain()`; a Swift re-encode doesn't round-trip
  `treasury_10y_pct` (the snake-case strategy capitalizes the `Y`).
- **Engine tests isolate `CACHE_DB`** (conftest does it) or they wipe the dev SQLite cache.
- **python.org Python 3.13 lacks root certs** — run the publisher scripts with
  `services/valuation-engine/.venv/bin/python` (has certifi).
- **Gradle caches test tasks**: env-var-driven runs need `--rerun`.
- **`Theme` is compile-time**: an `enum` of `static let` referenced 193× across 15 files, so no
  runtime theming (and no light mode) is possible until it is refactored. Sprint 5 Track B.
- **`simctl spawn … defaults write` does not set an app's preferences.** It writes a device-wide
  domain; the sandboxed app reads its own container plist first and only falls back to that domain
  for keys it lacks — so some writes appear to work and others silently don't. Edit
  `$(xcrun simctl get_app_container <UDID> com.swcsoftware.valuelens data)/Library/Preferences/com.swcsoftware.valuelens.plist`
  with `plutil` while the app is stopped. (Android: `adb shell run-as com.swcsoftware.valuelens`
  and `shared_prefs/valuelens.xml`.)
- **The share-site prototype is a mirror, not the app**: it re-implements the plain-language layer
  in JavaScript against the same report JSON. Core copy changes do not reach it automatically.

## Finishing a piece of work

Update `docs/TASKS.md` (check the box), `docs/ISSUES.md` (log what you found and did **not** fix —
minor bugs are deliberately deferred to a later bug-fix sprint), and `docs/SPRINTS.md` at sprint
close. Regenerate bundled samples and the web preview when model output changes. Then commit to
`dev`; merge to `staging` only with everything green.
