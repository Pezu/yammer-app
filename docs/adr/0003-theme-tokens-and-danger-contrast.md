# ADR-0003 — Complete the theme token set; darken `--danger` for AA contrast

Status: accepted · Epic: #1 · Date: 2026-10-24

## Context

`web/src/styles.scss` defines 7 theme tokens and the house rule says "theme tokens, no
hard-coded hex" — but the set has no success / warn / surface / overlay / focus-ring token, so
there was nothing to point at. The ux audit (ux.md, UX-C2) counted the result: 29 distinct
hard-coded hex colours across 23 files, including nine different greens all meaning "ok",
four ambers for "partial/warning", `#fff` ~60 times, two different overlay rgba values, and
literal restatements of tokens that do exist. Separately, white text on `--danger` (#ea4d4d)
measures 3.71:1 — below WCAG AA for normal text (approvals badge, confirm-dialog danger button).

This is one decision, not one per story — otherwise every ux story re-litigates its own green.

## Decision

Add to `styles.scss`. The values are chosen on MEASURED contrast, not frequency-in-tree — ux's
first proposal picked the most-used values (`#10b981`, `#f59e0b`), which measure 2.54:1 and
2.15:1 on white: they fail normal text, large text, and even the 3:1 non-text threshold, so a
status dot or border in them fails too. The revision passes in BOTH directions (as text on
white and as a fill under white text), because these tokens get used as label, border and
filled button on different screens, and a one-way token invites the next round of hard-coded
hex:

```
--success: #1f7a3d;        /* 5.37:1 both ways */   --success-soft: rgba(31, 122, 61, .12);
--warn: #92610a;           /* 5.33:1 both ways */   --warn-soft: rgba(146, 97, 10, .14);
--surface: #fff;           --overlay: rgba(18, 27, 46, .45);
--focus-ring: var(--primary);
```

Soft tints only ever sit behind `--text`; the bright greens survive as decorative fills behind
dark text, never as a label or an outline. And darken `--danger: #ea4d4d → #d93a3a` (4.55:1
with white — clears AA for normal text while keeping the hue; no need to go as dark as
`#c62828`).

Migration is incremental: hard-coded colours move to tokens as stories touch their files — no
big-bang sweep (CLAUDE.md §5 point 5). Until a file is touched, ux reviews treat a hard-coded
colour as a Note, not Blocking.

## Consequences

- Reviews get an objective rule again: once a token exists, a new hard-coded colour in a
  touched file is a house-rule violation with a named replacement.
- Every white-on-danger element clears AA centrally; no per-button large-text exceptions (the
  rejected alternative — a standing trap for every future danger button).
- `--danger` shifts an existing brand colour slightly; the change is visible on the login
  error, delete buttons and the approvals badge. Accepted as the smaller cost.
- Nine greens collapse to one semantic meaning; "partial" ambers likewise — screenshots across
  surfaces become comparable.
