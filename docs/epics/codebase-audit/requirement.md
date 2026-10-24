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
    double-printed **or silently not printed**), `sev:major` (a flow is broken or a
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
| `web/` backoffice surface | ux + architects | pending |
| `web/` waiter surface | ux + architects | pending |
| `web/` service board | ux + architects | pending |
| `web/` customer page | ux + architects | pending |
| `web/` `core/` + `shared/` | architects | pending |
| `bridge/mobile/` | architects | pending |
| Infra: `.github/workflows/`, `docker-compose.local.yml`, `db-seeds/`, docs | architects + qa | reviewed — findings (QA §11) |
| Test coverage (api + web suites) | qa | reviewed — findings (QA §11) |

## Disposition register

Every finding ID appears here exactly once. Disposition: **fix** (story/bug link),
**won't fix** / **deferred** (decision link).

| Finding | Disposition | Issue / decision |
|---|---|---|
| QA F-1 (stale login-roles doc) | fix | #6 |
| QA F-2 (contradictory service-day doc) | fix | #6 |
| QA F-3 (X-Forwarded-For trusted, QR throttle bypass) | pending triage | — |
| QA F-4 (documented `scripts/` missing) | pending triage | — |
| QA F-5 (AMOUNT payment loses discount) | fix | #5 (pending Q3) |
| QA §11 no-CI / DB-bound test | fix | #4 |
| QA §11 remaining coverage gaps (fiscal SM, pay, tenancy, web) | pending triage | — |

## Story map

Filled as findings are consolidated into issues (Phase 2). Format:

| Story/Bug | Track | Findings covered | Owner | Estimate | Stage |
|---|---|---|---|---|---|
| [#4 CI pipeline on PRs](https://github.com/Pezu/yammer-app/issues/4) | infra | QA §11 (no CI, DB-bound test); epic precondition | devops | S (proposed) | backlog |
| [#5 AMOUNT payment loses the table discount](https://github.com/Pezu/yammer-app/issues/5) | defect | QA F-5 | backend | pending Q3 | backlog |
| [#6 api/CLAUDE.md stale login + service-day sections](https://github.com/Pezu/yammer-app/issues/6) | docs | QA F-1, F-2 | backend | S (proposed) | backlog |
| _further rows as architect/ux findings land_ | | | | | |

## Decisions log

| # | Date | Decision | Why | Decided by |
|---|---|---|---|---|
| D1 | 2026-10-24 | `ci.yml` is the first task of the epic and blocks all implementation stories | DoD requires green CI; there is no PR CI today | epic body (lead) |
| D2 | 2026-10-24 | Won't-fix and deferred findings are recorded here, not deleted from the register | Epic requires nothing silently dropped | epic body (lead) |
| D3 | 2026-10-24 | Phase 2 has no priority ceiling: every accepted finding is implemented (answers Q1) | Human's call at the requirements gate | human (Radu) |
| D4 | 2026-10-24 | Bridge findings that require installing a new APK are deferred — recorded in the register and as backlog issues, not scheduled in this epic (answers Q2) | APK installs are a human gate; human chose to skip them for now | human (Radu) |

## Open questions

| # | Question | Asked to | Status |
|---|---|---|---|
| Q1 | Is there a budget/priority ceiling for Phase 2 — do we implement everything accepted, or stop after critical/major + High-impact? | lead → human | Answered → D3 |
| Q2 | Should `bridge/mobile/` findings that require installing a new APK be scheduled in this epic, given installs are a human gate? | lead → human | Answered → D4 |
| Q3 | AMOUNT payment on a discounted table (bug #5): should it allocate against net (discounted) prices like FULL/PARTIAL (recommended), or stay "taken as is" and be blocked on discounted tables? | lead → human | Open |
