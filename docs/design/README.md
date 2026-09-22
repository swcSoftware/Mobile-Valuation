# Design working files

Not shipped, not built, not tested by CI. These exist so a future session can continue the Sprint 5
design work instead of rebuilding it. The reasoning lives in [../DESIGN.md](../DESIGN.md).

## `preview/valuelens-preview.html`

A standalone HTML mirror of both apps — the **tester share site**. One file: CSS, JS, and sample
report JSON for eleven filers embedded in a `<script id="data" type="application/json">` block. No
build step, no dependencies, no network except Google Fonts.

It carries **both layouts** behind the same data, which is the point: Classic is the shipped dark
app, Report card is the Sprint 5 direction, and the Settings screen switches layout, theme
(light/dark/system) and accent color independently.

- Open it locally: any static server, e.g. `python3 -m http.server 8777` in that folder.
  (It needs `http://`, not `file://` — `localStorage` is used for the identity gate.)
- Publish it for phone testing: it is published as a private claude.ai artifact the owner opens on
  a real iPhone. Replace the artifact's content with this file; do not create a second one.
- Regenerate the sample data: see the `VL_DUMP_TICKERS` command in [../DESIGN.md](../DESIGN.md).

The eleven filers are chosen to be awkward, not flattering. The ones that matter when judging a
design change:

| Ticker | Why it is in here |
|---|---|
| **CRWV** | On the cash-flow model the DCF is negative, so **no value is shown**. The withheld state. |
| **MCD** | Negative shareholders' equity, so debt-to-equity has no meaning — the ungradable tile. |
| **AGNC** | Mortgage REIT: no revenue tag, so *none* of the four health facts compute. |
| **BRK-B** | Two share classes; TTM EPS is derived rather than reported. |
| **O** | A REIT graded on an operating-company rule — the sector-fairness problem (ISSUES #82). |
| **PLTR** | −1272% margin; the case that stress-tests any price/value scale. |

## `directions/*.dc.html`

The three explored directions as self-contained artboards (390×844), each driven by the same real
engine output. `A` and `B` were not chosen; `B` ("The Gap") is kept deliberately as a future
direction rather than deleted. `C` is the one being built.

Each is a Design Component page: open it through the Design canvas it was published to, or read the
markup directly. The logic block at the bottom holds the embedded dataset and the render values.
