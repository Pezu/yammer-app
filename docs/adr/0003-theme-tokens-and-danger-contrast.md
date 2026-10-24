# ADR-0003 — Complete the theme token set; darken `--danger` for AA contrast

Status: accepted · Epic: #1 · Date: 2026-10-24

## Context

`web/src/styles.scss` defines 7 theme tokens and the house rule says "theme tokens, no
hard-coded hex" — but the set has no success / warn / surface / overlay / focus-ring token, so
there was nothing to point at. The ux audit (ux.md, UX-C2) counted the result: 29 distinct
hard-coded hex colours across 23 files, including nine different greens all meaning "ok",
four ambers for "partial/warning", `#fff` ~60 times, two different overlay rgba values, and
literal restatements of tokens that do exist. Separately, white text on `--danger` (#ea4d4d)
measures 3.3:1 — below WCAG AA for normal text (approvals badge, confirm-dialog danger button).

This is one decision, not one per story — otherwise every ux story re-litigates its own green.

## Decision

Add to `styles.scss`, values taken from what the code already uses most:

```
--success: #10b981;        --success-soft: rgba(16, 185, 129, .12);
--warn: #f59e0b;           --warn-soft: rgba(245, 158, 11, .14);
--surface: #fff;           --overlay: rgba(18, 27, 46, .45);
--focus-ring: var(--primary);
```

and darken `--danger: #ea4d4d → #d93a3a` (4.0:1 with white — clears AA for normal text while
keeping the hue).

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
