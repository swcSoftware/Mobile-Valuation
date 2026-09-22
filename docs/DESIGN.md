# ValueLens — UI direction

Status: **Sprint 5 in progress, design phase.** No Swift or Compose work has started. Written
2026-09-22.

Read this with [TASKS.md](TASKS.md) Sprint 5, which holds the task list. This file holds the
*reasoning* — the directions explored, the decisions taken, and the rules a layout has to obey.

## Why this sprint exists

The owner's read of the shipped app, 2026-09-22:

> "it is very information heavy and the design looks very encyclopedia/book like. It doesn't feel
> like a modern and high fidelity financial app."

That is a fair description of `CompanyDetailView`: a 373-line vertical stack of section headers and
cards that renders everything the core produces, in the order the core produces it. It is honest and
dense. It is not designed.

## What was explored

Three phone artboards, each driven by **real engine output** for nine filers chosen to be awkward
(AAPL, MCD, BRK-B, JPM, PGR, O, AGNC, CRWV, PLTR) rather than flattering. Sources are in
[`design/directions/`](design/directions/); they were published to a Design canvas for review.

| | Direction | Idea | Outcome |
|---|---|---|---|
| A | **Verdict first** | Deep ink, condensed grotesque. One word, one sentence, two numbers, four reasons; everything else a tap away. | Not chosen. Kept — it is the natural dark counterpart to C. |
| B | **The Gap** | Instrument black, mono. The price/value gap drawn as a measuring instrument: one scale, real ticks, the gap shaded between the marks. | **Not built.** The most ownable and the most expensive; a genuinely different information design. Kept as a future direction. |
| C | **Report card** | Light ground, graded health facts with printed thresholds. | **Chosen by the owner**, 2026-09-22, "but it needs some further focus and improvement first". |

The structural observation that decided scope: **A and C are the same information design in
different clothes** (a card-and-section stack; the difference is theme, type and density). Once
theming is resolved at runtime, the second one is nearly free. B is not, so B waits.

## Decisions

### Taken by the owner, 2026-09-22

1. **The new layout is an add-on, not a replacement.** The existing dark layout stays exactly as it
   is and stays the default; the new one arrives beside it; a Settings toggle picks between them.
2. **Health facts carry two judgements, not one** — an absolute grade against a printed rule *and*
   the same number against the company's own filed history. They answer different questions ("is
   this good?" vs "is this normal for them?").
3. **Theme is independent of layout**: Light / Dark / System, either layout on either ground. The
   longer goal is a **custom accent picker** so the user chooses their own colors — consistent with
   the owner's other apps.
4. **Keep the model toggle as it is** — one model at a time, not both side by side.
5. **Price vs value leads the screen**; business health sits below it.

### Taken by Claude, open to reversal

- **The accent colors chrome only.** Price stays amber and fair value stays mint in every scheme.
  That pair is how the app is read; if a user could set both, the core reading breaks. Flagged to the
  owner as a constraint to overrule if they disagree.
- **Accent foreground is computed** from relative luminance rather than assumed, or a pale accent
  gives white-on-white buttons.
- **Health facts became full-width rows**, not a 2×2 grid. Two judgements plus a sparkline do not fit
  a half-width tile legibly at 375px.

### Still open — these block the Swift work

- **The four thresholds are Claude's invention and are not approved**: A at 20% return on capital,
  0.30× debt-to-equity, 1.00× cash conversion, 15%/yr revenue growth.
- **Fixed thresholds are unfair across sectors** (ISSUES #82). Realty Income grades D on return on
  capital at 5.1%, which is ordinary for a REIT. Either thresholds vary by `sector.mode`, or the tile
  declines to grade and says the rule does not fit this filer.

## The four things every layout must carry

These are the reasons the app is trustworthy, and they are exactly what a second layout can silently
drop. Today each lives in one place in `CompanyDetailView`; with two layouts that becomes two, so
they stop being inline markup and become **components a layout is required to place**, with a shared
test asserting both layouts render all four.

1. **Value withheld** — `checksFailed`, and `verdict == .insufficientData`. It must read as a
   deliberate act of honesty, not an error or an empty state. Live case: **CRWV on Model B**, where
   the discounted cash flow comes out negative and no number is shown.
2. **Data notes and one-off flags** — `warnings` and the non-passing `dataChecks`. Live cases: MCD's
   share-scale correction (#77), BRK-B's derived TTM EPS, PLTR's split restatement.
3. **Assumed vs measured** — `provenance`. Beta is measured; cost of debt often is not. An unlabeled
   input is a bug (non-negotiable 2, ISSUES #31).
4. **Sector mode** — an operating company, a bank and a REIT are not valued the same way, and the
   screen says which.

## The architecture the layouts sit on

Three **independent** axes. The mistake to avoid is letting layout imply theme, which is what the
first prototype did.

| Axis | Chosen by | Controls |
|---|---|---|
| **Theme** | Light / Dark / System | ground, text, and the semantic colors adjusted per ground |
| **Accent** | the user, eventually any color | chrome only: buttons, tabs, selection, focus |
| **Layout** | Classic / Report card | typography and components. **Never sets a color token.** |

Four combinations must hold, including the two that are new: **classic on light** and **report card
on dark**.

`Theme` is currently an `enum` of `static let` constants referenced **193× across 15 files**
(`apps/ios/ValueLens/DesignSystem/Theme.swift`), so none of this is possible until those tokens
resolve at runtime. That refactor is **global** under non-negotiable 4 and gets its own commit saying
so; it must be a no-visual-change refactor for the existing dark app, verifiable by screenshot diff
before the new layout lands. Android's `ui/theme/Theme.kt` needs the same seam.

**Layouts read only** `ValuationReport`, `ModelResult` and `ExplainSummary`. No layout may compute a
number, a label or a verdict — those come from the core, which is what stops two layouts from
disagreeing. `ExplainSummary` (`Explain.kt` → `explainJson`) already supplies the verdict sentence,
the four health facts and the checks summary to both platforms.

**Expert Mode stays one shared presentation** for both layouts. Two layouts × two modes would be
four states to keep honest; this keeps it to three that actually differ.

## Semantic colors

| Token | Dark | Light | Meaning |
|---|---|---|---|
| `price` | `#F5A623` | `#9A6006` | market price — always amber |
| `value` | `#2ED99E` | `#0B7A57` | intrinsic / fair value — always mint |

Mint and amber both fail contrast as text on a light ground at their dark-theme values, which is why
the light column is darker. Neither is ever replaced by the user's accent.

## The iteration loop

Design revisions go to the **tester share site** — a standalone HTML mirror of both apps, kept at
[`design/preview/valuelens-preview.html`](design/preview/valuelens-preview.html) and published to a
private artifact the owner opens on a real iPhone. Then a round of questions and owner feedback, then
the next revision. **No Swift work starts on the report-card body until the design is signed off.**

The preview is a mirror, not the app: it re-implements the plain-language layer in JavaScript
(`verdictSentence`, `healthFacts`) against the same report JSON. When the core's copy changes, the
mirror has to be updated by hand — it is a design tool, not a second implementation to maintain.

Regenerate its sample data with the core itself:

```bash
cd apps/android && VL_DUMP_DIR=/tmp/ui VL_DUMP_TICKERS="AAPL,MCD,BRK-B,JPM,PGR,O,AGNC,CRWV,PLTR" \
  SEC_USER_AGENT="Name email" ./gradlew :valuation-core:desktopTest --tests '*SampleDump*' --rerun
```

then replace the `<script id="data" type="application/json">` payload in the preview.

## What the prototype already proved

Building it against real filers rather than mockup data found four bugs in the design itself, all
fixed in the prototype and logged for the real implementation:

- Revenue-growth history compared revenue **level**, which reads "best in 10 years" for any healthy
  company and says nothing. It now compares the 5-year rate against the full-period rate.
- CRWV showed "faster than its 2-yr rate". Under **five** filed years there is now no historical read
  at all, because that is not a trend.
- AGNC rendered four empty tiles. A filer where none of the four facts compute now gets a designed
  state naming the filer type (ISSUES #83).
- Missing numbers printed their units: "—× equity", "— / yr".

One gap in the data, not the design: the annual history series carries `equity` but not `total_debt`,
so debt load is the only fact with no trend line (ISSUES #81, Sprint 5 Track F).
