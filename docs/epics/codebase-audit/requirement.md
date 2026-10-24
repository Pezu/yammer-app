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

1. **Coverage** — every module in scope (`api/`, `web/`, `bridge/mobile/`, repo
   infrastructure) is examined by at least one auditor and listed in the findings
   register, even if a module yields zero findings (recorded as "reviewed, no findings").
2. **Traceability** — every finding has exactly one disposition: a linked story/bug
   issue, or a "won't fix" entry in this page's decisions log with a reason.
3. **Delivery** — every accepted finding's story/bug reaches `stage:qa-passed` on the
   integration stack built from `develop`.
4. **No regressions** — all existing tests keep passing (`api`: `mvn test`,
   `web`: `npm test`) and, once `ci.yml` exists, CI is green on every PR of this epic.

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

- `api/` — Spring Boot 3.2 / Java 21 (204 source files): tenancy, service days, table
  rounds, payments + fiscal outbox, WebSocket push, reports, Netopia
- `web/` — Angular 21 zoneless (84 TS + 56 template/style files), four surfaces
- `bridge/mobile/` — Kotlin Android fiscal bridge (19 files)
- Repo infrastructure: `docker-compose.local.yml`, `.github/workflows/`, `db-seeds/`, docs

**Known precondition gap:** `.github/workflows/ci.yml` does not exist, so the Definition
of Done's "green CI" cannot be met today. Creating it (build + tests + release gate) is
the first `devops` task of this epic and blocks the merge of every implementation story.

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
    cross-tenant leak, fiscal double-print), `sev:major` (a flow is broken or a business
    rule violated, workaround exists), `sev:minor` (incorrect but low-impact behaviour),
    `sev:trivial` (cosmetic).
  - Improvements use impact **High / Medium / Low** = (who is affected) × (how often) ×
    (cost of leaving it). High: affects money, tenancy, fiscal, or a daily waiter/customer
    flow. Medium: affects an admin flow or maintainability of a sensitive area. Low:
    polish, consistency, dev ergonomics.
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
- Stories meet the Definition of Ready before `stage:ready`: user-story form,
  Given/When/Then acceptance criteria (testable, confirmed by `qa`; money criteria with
  concrete amounts; waiter/customer copy in RO and EN), technical tasks linked by the
  architects, UI stories specified in `ux.md`.
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

## Story map

Filled as findings are consolidated into issues (Phase 2). Format:

| Story/Bug | Track | Findings covered | Owner | Estimate | Stage |
|---|---|---|---|---|---|
| _pending — audit in progress_ | | | | | |

## Decisions log

| # | Date | Decision | Why | Decided by |
|---|---|---|---|---|
| D1 | 2026-10-24 | `ci.yml` is the first task of the epic and blocks all implementation stories | DoD requires green CI; there is no PR CI today | epic body (lead) |
| D2 | 2026-10-24 | Won't-fix and deferred findings are recorded here, not deleted from the register | Epic requires nothing silently dropped | epic body (lead) |

## Open questions

| # | Question | Asked to | Status |
|---|---|---|---|
| Q1 | Is there a budget/priority ceiling for Phase 2 — do we implement everything accepted, or stop after critical/major + High-impact? | lead → human | Open |
| Q2 | Should `bridge/mobile/` findings that require installing a new APK be scheduled in this epic, given installs are a human gate? | lead → human | Open |
