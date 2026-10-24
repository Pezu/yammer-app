# Codebase audit — requirement

Epic: [#1](https://github.com/Pezu/yammer-app/issues/1) · Owner: `ba` · Status: Phase 1 (audit) in progress

## Context

Yammer is a restaurant point-of-sale serving four surfaces (backoffice, waiter phone app,
kitchen/bar service board, public customer QR page) plus an on-prem Android fiscal bridge.
The product was rebuilt from scratch starting 2026-08-29 and is live in production
(`yammer.ro`), deployed on every push to `main`. The rebuild moved fast; nobody has yet
stepped back and examined the whole codebase (~39.5k LOC) with fresh eyes.

The raw requirement: **investigate the whole codebase; propose needed UX/UI improvements,
architecture improvements/fixes and defects; implement the agreed items.**

This is an *audit-then-implement* epic, not a feature epic. Phase 1 produces an
evidence-backed findings register; Phase 2 implements the accepted findings as normal
stories and bugs through the standard pipeline.

## Goal and success metrics

**Goal:** a complete, prioritized findings register across three tracks, with every
accepted finding implemented on `develop` and every rejected finding recorded with its
reason — nothing silently dropped.

Success is measured by:

1. **Coverage** — every unit in the Coverage checklist below is examined by at least
   one auditor and marked *reviewed — findings* or *reviewed — no findings*. "Examined"
   is claimed per unit, never per module.
2. **Traceability** — every finding ID from `design.md`, `ux.md` and the QA register
   appears **exactly once** in the Disposition register below, linked to a story/bug
   issue or to a decision entry (won't fix / deferred) with its reason. At epic exit,
   criterion 2 is a diff between the registers' ID lists and this table — not a reading
   exercise.
3. **Delivery** — every accepted finding's story/bug reaches `stage:qa-passed` on the
   integration stack built from `develop`.
4. **No regressions** — all existing tests keep passing (`api`: `mvn test`,
   `web`: `npm test`), the regression blocks of `test-plan.md` §9 for the invariants a
   story touches are green, and, once `ci.yml` exists, CI is green on every PR of this
   epic.

## Personas served

| Persona | Surface | What the audit protects for them |
|---|---|---|
| ADMIN / SUPER | Backoffice | Correct reports and money figures, consistent CRUD UX |
| WAITER | Phone app | Fast, unambiguous flows on a phone in a noisy room; service-day scoping |
| SERVICE | Kitchen/bar board | Live, reliable order push; readable at a distance |
| Customer | Public QR page | Ordering that works on any phone, in RO and EN, without a login |
| Maintainers | Repo | An architecture that stays safe to change (tenancy, money, fiscal invariants) |

## Scope

Three audit tracks, each owned by the role that will defend its findings:

| Track | Auditor(s) | Findings doc |
|---|---|---|
| Architecture improvements/fixes + defects | `architect-a`, `architect-b` | `design.md` |
| UX/UI improvements | `ux` | `ux.md` |
| Testability / quality gaps | `qa` | feeds both docs + `test-plan.md` |

Code in scope:

- `api/` — Spring Boot 3.2 / Java 21 (~200 source files): tenancy, service days, table
  rounds, payments + fiscal outbox, WebSocket push, reports, Netopia
- `web/` — Angular 21 zoneless (~80 TS + ~60 template/style files), four surfaces
- `bridge/mobile/` — Kotlin Android fiscal bridge (~20 files)
- Repo infrastructure: `docker-compose.local.yml`, `.github/workflows/`, `db-seeds/`, docs

**Known precondition gap:** `.github/workflows/ci.yml` does not exist, so the Definition
of Done's "green CI" cannot be met today. Creating it (build + tests + release gate) is
the first `devops` task of this epic and blocks the merge of every implementation story.
`mvn test` includes a `@SpringBootTest` that needs a live PostgreSQL (no test datasource,
no Testcontainers exist today), so running the suite unattended — CI provisioning a
database — is part of that task's scope, not an afterthought.

## Out of scope

Per the epic body:

- New product features not implied by a finding
- Database schema rewrites — targeted `V<n>__*.sql` additions are allowed; `V1`/`V2` are
  immutable (never edit an applied migration)
- Anything executed in the GCP console — prepared as a `deploy.yml` change plus console
  steps for the human, never executed by an agent
- Releasing `develop → main` (always the human)

## How findings are triaged

Every finding enters the register (in `design.md` / `ux.md`) with:

- **ID** (`ARCH-nn`, `UX-nn`, `DEF-nn`, `QA-nn` for test-coverage gaps), one-line title,
  module and file(s), evidence (what was observed, where)
- **Classification** — defect (observable wrong behaviour), architecture risk (correct
  today, fragile or costly tomorrow), or UX/UI improvement
- **Severity / impact:**
  - Defects use the bug severity labels: `sev:critical` (money wrong, data loss,
    cross-tenant leak, a role reaching something it must not, a fiscal receipt
    double-printed, or a payment reaching a terminal state with no receipt issued and no
    operator verdict — a PENDING whose bridge is merely offline is queued inside the 180 s
    deadline and is NOT this case), `sev:major` (a flow is broken or a
    business rule violated, workaround exists), `sev:minor` (incorrect but low-impact
    behaviour), `sev:trivial` (cosmetic).
  - Improvements use impact **High / Medium / Low**. High: affects money, tenancy,
    fiscal, or a daily waiter/customer flow. Medium: affects an admin flow or
    maintainability of a sensitive area. Low: polish, consistency, dev ergonomics.
- **Disposition** (decided in triage with the lead; human sign-off per the §9
  requirements gate): *fix now* (story/bug this epic), *won't fix* (recorded below with
  reason), or *deferred* (recorded below; a backlog issue is created but not scheduled).

Auditors own their registers (`design.md`, `ux.md`); the consolidated view — every finding
ID with its disposition and story/bug link — is this page's story map, owned by `ba`.

Triage order: critical/major defects first, then High-impact improvements, then the rest.
Anything touching payments, fiscal, tenancy, live push, or schema also needs the
architecture sign-off gate (CLAUDE.md §9).

## Story breakdown rules

- Accepted findings are **grouped into coherent stories** — per surface or per theme
  (e.g. "waiter payments page fixes", "AccessGuard coverage"), never one story per nit.
- Genuine defects become `bug` issues with a severity label; improvement groups become
  `story` issues. All carry `Part of #1`, an owner label, a stage label, dependencies as
  `Blocked by #n`, and an S/M/L estimate agreed with the implementing role.
- Each story states which invariants it comes near (I1–I9 in `test-plan.md` §3 —
  tenancy, money, settled lines, fiscal outbox, service day, roles, table rounds, live
  push, RO/EN); that list selects the regression blocks for success criterion 4 and
  QA's exit check.
- Stories meet the Definition of Ready before `stage:ready`: user-story form,
  Given/When/Then acceptance criteria (testable, confirmed by `qa`; money criteria with
  concrete amounts; waiter/customer copy in RO and EN), technical tasks linked by the
  architects, UI stories specified in `ux.md`. Acceptance criteria are written to the
  `qa` checklist in `test-plan.md` §7 (named role, concrete amounts, observable result,
  404 for tenancy, RO+EN copy, service-day not date, named fiscal state, named
  regression scope for no-behaviour-change work).
- Anything estimated over ~3 days is split.

## What "done" means for this epic

1. Findings register complete (coverage criterion above) and triaged with the lead and
   the human (requirements gate).
2. Every accepted finding shipped: merged to `develop` via a reviewed PR, dev-verified,
   QA-passed on the integration stack; no open critical/major bugs from this epic.
3. Every rejected/deferred finding recorded in the Decisions log below with its reason.
4. Docs updated: `design.md`, `ux.md`, `api.md`, `ui-flows.md`, `test-plan.md` /
   `test-report.md`, `release-notes.md`; module `CLAUDE.md` files updated where documented
   behaviour changed.
5. The epic closes only when the human has merged `develop → main` and the deploy is green.

## Coverage checklist

One row per meaningful unit; auditors mark each *reviewed — findings* or *reviewed — no
findings*. The epic cannot pass success criterion 1 with a pending row.

| Unit | Auditor | Status |
|---|---|---|
| `api/` security (`security/`, auth, JWT, throttling) | architects | pending |
| `api/` services (`service/` — money, rounds, shifts, approvals) | architects | pending |
| `api/` persistence (`repository/`, entities, `db/migration`) | architects | pending |
| `api/` controllers + DTOs | architects | pending |
| `api/` WebSocket (`ws/`, bridge handler, order push) | architects | pending |
| `web/` backoffice surface | ux + architects | ux: reviewed — findings (ux.md); architects: pending |
| `web/` waiter surface | ux + architects | ux: reviewed — findings (ux.md); architects: pending |
| `web/` service board | ux + architects | ux: reviewed — findings (ux.md); architects: pending |
| `web/` customer page | ux + architects | ux: reviewed — findings (ux.md); architects: pending |
| `web/` `core/` + `shared/` | architects | pending |
| `bridge/mobile/` | architects | pending |
| Infra: `.github/workflows/`, `docker-compose.local.yml`, `db-seeds/`, docs | architects + qa | reviewed — findings (QA §11) |
| Test coverage (api + web suites) | qa | reviewed — findings (QA §11) |

## Disposition register

Every finding ID appears here exactly once. Disposition: **fix** (story/bug link),
**won't fix** / **deferred** (decision link).

QA ids map positionally to the test plan's §11: QA-01…QA-14 = G-1…G-14,
QA-15…QA-21 = F-1…F-7, QA-22 = F-8 (reported after the plan's first draft).

| Finding | Disposition | Issue / decision |
|---|---|---|
| QA-01 (fiscal state machine untestable at current level) | pending triage | — |
| QA-02 (no e2e layer) | pending triage | — |
| QA-03 (web surfaces untested) | pending triage | — |
| QA-04 (`OrderService.pay` PARTIAL/AMOUNT untested) | pending triage | — |
| QA-05 (tenant isolation untested) | pending triage | — |
| QA-06 (no permission test) | pending triage | — |
| QA-07 (no CI) | fix | #4 |
| QA-08 (Spring test needs a live DB) | fix | #4 (test datasource in scope) |
| QA-09 (untested services) | pending triage | — |
| QA-10 (no concurrency test) | pending triage | — |
| QA-11 (untranslated RO values unchecked) | pending triage | — |
| QA-12 (bridge frame content unasserted) | pending triage | — |
| QA-13 (stack not buildable from `develop`) | fix | #8 |
| QA-14 (`bridge/mobile` no tests; pure-logic units allowed under D4) | pending triage | — |
| QA-15 / F-1 (stale login-roles doc) | fix | #6 (per D7: doc fix only) |
| QA-16 / F-2 (contradictory service-day doc) | fix | #6 |
| QA-17 / F-3 (X-Forwarded-For trusted, QR throttle bypass) | pending triage | — |
| QA-18 / F-4 (documented `scripts/` missing) | fix | #8 |
| QA-19 / F-5 (AMOUNT payment loses discount) | fix | #5 (per D5: option (a)) |
| QA-20 / F-6 (payment type resolved twice — trivial) | pending triage | — |
| QA-21 / F-7 (RECEIPT queue behaviour undocumented) | fix | #6 |
| QA-22 / F-8 (any role can re-issue a fiscal receipt) | fix | #9 |
| UX group 1 (UX-C3, W1, W6, W14, W18 — waiter error handling/state loss) | fix | story to file (ux.md §10.1) |
| UX group 2 (UX-Q1, Q3, Q4, Q5 — customer menu legibility + cart) | fix | story to file (ux.md §10.2) |
| UX group 3 (UX-C1 — money formatting) | fix | story to file (ux.md §10.3) |
| UX group 4 (UX-C2, C4, C10, C11 — tokens/focus/combo keyboard) | fix | story to file (ux.md §10.4; needs architects' token decision) |
| UX group 5 (UX-S1, S2, S3, S5, S6 — service board viewing distance) | fix | story to file (ux.md §10.5) |
| UX group 6 (UX-C6, C7, C8, Q10 — compliance + dead ends) | fix | story to file (ux.md §10.6; needs Q4) |
| UX group 7 (UX-W2, W3, W10 — waiter board truth) | fix | story to file (ux.md §10.7) |
| UX group 8 (UX-W7, W8, W9 — fiscal visibility for the waiter) | fix | story to file (ux.md §10.8) |
| UX group 9 (UX-W4, W5, W15, S4 — confirmations on destructive actions) | fix | story to file (ux.md §10.9) |
| UX group 10 (UX-C5 + §8 copy corrections — i18n coverage) | fix | story to file (ux.md §10.10) |
| UX group 11 (UX-B1…B8, C9, C12 — backoffice polish) | fix | story to file (ux.md §10.11) |
| UX group 12 (UX-W11…W13, W16, W17, W19…W21, Q6, Q7, Q8, Q12, Q13, Q14 — smaller items) | fix | story to file (ux.md §10.12) |
| UX-B9 (Order Points column density) | pending Q6 | — |
| UX-Q11 (customer menu search) | pending Q5 | — |
| UX-Q15 (legal label language) | pending Q4 | — |

## Story map

Filled as findings are consolidated into issues (Phase 2). Format:

| Story/Bug | Track | Findings covered | Owner | Estimate | Stage |
|---|---|---|---|---|---|
| [#4 CI pipeline on PRs](https://github.com/Pezu/yammer-app/issues/4) | infra | QA-07, QA-08; epic precondition | devops | S (proposed) | backlog |
| [#5 AMOUNT payment loses the table discount](https://github.com/Pezu/yammer-app/issues/5) | defect | QA-19 (F-5) | backend | S (proposed) | backlog |
| [#6 api/CLAUDE.md stale login + service-day sections](https://github.com/Pezu/yammer-app/issues/6) | docs | QA-15, QA-16, QA-21 | backend | S (proposed) | backlog |
| [#8 develop builds its own stack: commit compose/nginx/CLAUDE.md + scripts](https://github.com/Pezu/yammer-app/issues/8) | infra | QA-13, QA-18 | devops | S (proposed) | backlog |
| [#9 any role can re-issue a fiscal receipt](https://github.com/Pezu/yammer-app/issues/9) | defect | QA-22 (F-8) | backend | S (proposed) | backlog |
| UX groups 1–12 (ux.md §10) | ux/ui | 60 UX findings | frontend | per story | stories after gate |
| _further rows as architect/ux findings land_ | | | | | |

## Decisions log

| # | Date | Decision | Why | Decided by |
|---|---|---|---|---|
| D1 | 2026-10-24 | `ci.yml` is the first task of the epic and blocks all implementation stories | DoD requires green CI; there is no PR CI today | epic body (lead) |
| D2 | 2026-10-24 | Won't-fix and deferred findings are recorded here, not deleted from the register | Epic requires nothing silently dropped | epic body (lead) |
| D3 | 2026-10-24 | Phase 2 has no priority ceiling: every accepted finding is implemented (answers Q1) | Human's call at the requirements gate | human (Radu) |
| D4 | 2026-10-24 | Bridge findings that require installing a new APK are deferred — recorded in the register and as backlog issues, not scheduled in this epic (answers Q2) | APK installs are a human gate; human chose to skip them for now | human (Radu) |
| D5 | 2026-10-24 | AMOUNT allocates against net (discounted) prices like FULL/PARTIAL — the table's discount applies in AMOUNT mode too, paying the displayed total settles the table (answers Q3, bug #5 option (a)) | Consistency across payment modes; the bill screen shows net totals | human (Radu) |
| D6 | 2026-10-24 | The Netopia sandbox base URL in the production `deploy.yml` is deliberate for now — no switch-to-live task in this epic | Human's call; note the intent wherever a finding touches it | human (Radu) |
| D7 | 2026-10-24 | Password login stays open to any role — QA F-1 resolves as a doc fix only (`api/CLAUDE.md` corrected, no code change) | The web app routes every role to its surface; restricting login was never the rebuilt behaviour | human (Radu) |

## Open questions

| # | Question | Asked to | Status |
|---|---|---|---|
| Q1 | Is there a budget/priority ceiling for Phase 2 — do we implement everything accepted, or stop after critical/major + High-impact? | lead → human | Answered → D3 |
| Q2 | Should `bridge/mobile/` findings that require installing a new APK be scheduled in this epic, given installs are a human gate? | lead → human | Answered → D4 |
| Q3 | AMOUNT payment on a discounted table (bug #5): should it allocate against net (discounted) prices like FULL/PARTIAL (recommended), or stay "taken as is" and be blocked on discounted tables? | lead → human | Answered → D5 |
| Q4 | Legal pages (UX-C6/UX-Q10): the six legal links and bad QR codes dead-end on the staff login form — the Netopia/ANPC compliance surface of a card-accepting site. ux specced the shells; the content must come from the human. (a) route + shells now, human supplies content; (b) **recommended**: route + shells now with a minimal factual placeholder, content follows when provided; (c) defer entirely | lead → human | Open |
| Q5 | Customer menu search (UX-Q11): the waiter app has search, the customer page does not. It is a new feature, not a defect — the epic's out-of-scope rule says features only when implied by a finding. (a) in scope this epic; (b) **recommended**: defer — record + backlog issue | lead → human | Open |
| Q6 | Order Points density (UX-B9): 13 editable columns. (a) **recommended**: ux's two cheap tweaks only this epic, redesign deferred as its own backlog story; (b) full redesign story now | lead → human | Open |
