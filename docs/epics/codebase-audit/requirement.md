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

Cross-track duplicates get ONE canonical id — first/most complete report wins — and the other
ids live in the "Also reported as" column: one disposition, one story per real-world defect.
This register is the single authority for every alias (incl. the `QA-nn` series, defined only
here: QA-01…14 = G-1…14, QA-15…22 = F-1…8, QA-23 = F-9, QA-24 = F-10).

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
| `api/` security (`security/`, auth, JWT, throttling) | architects | reviewed — findings (design.md) |
| `api/` services (`service/` — money, rounds, shifts, approvals) | architects | reviewed — findings (design.md) |
| `api/` persistence (`repository/`, entities, `db/migration`) | architects | reviewed — findings (design.md) |
| `api/` controllers + DTOs | architects | reviewed — findings (design.md) |
| `api/` WebSocket (`ws/`, bridge handler, order push) | architects | reviewed — findings (design.md) |
| `web/` backoffice surface | ux + architects | reviewed — findings (ux.md, design.md) |
| `web/` waiter surface | ux + architects | reviewed — findings (ux.md, design.md) |
| `web/` service board | ux + architects | reviewed — findings (ux.md, design.md) |
| `web/` customer page | ux + architects | reviewed — findings (ux.md, design.md) |
| `web/` `core/` + `shared/` | architects | reviewed — findings (design.md) |
| `bridge/mobile/` | architects | reviewed — findings (design.md) |
| Infra: `.github/workflows/`, `docker-compose.local.yml`, `db-seeds/`, docs | architects + qa | reviewed — findings (design.md, QA §11) |
| Test coverage (api + web suites) | qa | reviewed — findings (QA §11) |

## Disposition register

Every finding ID appears here exactly once (canonical id; duplicates in "Also reported as").
Disposition: **fix** (story/bug link), **won't fix** / **deferred** (decision or design ref).

### Critical / major defects and fixes

| Finding | Also reported as | Disposition | Issue |
|---|---|---|---|
| A1 (forged/self-issued Netopia IPN) | qa R-O5b evidence | fix | #13 |
| A2 (double-payment race) | QA G-10 partially | fix | #14 |
| A3 (duplicate order numbers) | — | fix | #15 |
| A4 (waiter lines trust client prices) | architects' W1 | fix | #16 |
| A5 (CANCELED hides settled money) | — | fix | #17 |
| A6 (orders report in-memory paging) | — | fix | #21 |
| A7 (approvals 403 instead of 404) | — | fix | #18 |
| A8 (server-rendered times UTC — printed slips behind) | pairs with UX-B10/#44 (three-clocks defect; must land together, R-S8/R-S9) | fix | #24 |
| A9 (money/permission/tenancy untested) | QA G-1…G-14 umbrella | fix | #26, #27, #28 (+ test ACs in #14) |
| F-5 / QA-19 (AMOUNT ignores discount; API-only reach — UI hides the mode on discounted tables) | ux reach note | fix (D5) + FE rider: unhide AMOUNT | #5 (arithmetic rides #14) |
| F-8 / QA-22 (fiscal re-issue by read-only roles; held at major per design §9) | — | fix | #9 |
| F-9 / QA-23 (footer shows Rendezvous identity to all tenants) | UX-C8 | fix | #11 |
| B-T1 (bridge layer tenant-blind) | — | fix | #19 |
| W2 (JWT in the WebSocket URL) | architects' A13-jwt | fix | #20 |
| I1 / QA-07 (no ci.yml) | QA G-7 | fix | #4 |
| I6 (Spring Boot 3.2 EOL) | — | fix (human gate: dependency) | #22 |
| I9 (main/develop Flyway divergence) | — | fix (human gate: release) | #45 (runbook) |

### Minor / trivial findings

| Finding | Also reported as | Disposition | Issue |
|---|---|---|---|
| A10, A11, A12, A14 (races + join throttle) | — | fix | #23 |
| A13-key (bridge key in query) | — | fix (rides W2) | #20 |
| I11 (unconfigured RestClient inside the start() transaction) | — | fix (rides A1, design §3.1) | #13 |
| A15 (waiter retry not assignment-scoped) | — | won't fix — intended, documented (design §2.3) | doc line in #6 |
| A16 (+I4, W9: doc drift) | QA F-1/F-2/F-7 = QA-15/16/21 | fix (D7 for F-1) | #6 |
| A17 / QA-20 (payment type resolved twice) | QA F-6 | fix (rides #14's task) | #14 |
| F-3 / QA-17 (X-Forwarded-For trust) | — | fix | #23 |
| F-10 / QA-24 (legal links dead-end on login) | UX-C6, UX-Q10 | fix (D8) | #34 |
| W3 (index.html uncached-control) | — | fix | #24 |
| W5 (combo-box no ARIA/keyboard) | UX-C4/C10 area | fix | #32 |
| W6 (401 bounce on public surface) | — | fix | #34 |
| I3 (deploy.yml no concurrency) | — | fix | #24 |
| I5 (stack not buildable from develop) | QA G-13 = QA-13, F-4 = QA-18 | fix | #8 |
| B1, B2, B3 (bridge I/O, budget, frame answers) | — | fix; device verification deferred (D4) | #25 |
| B4, B5, B6, W7, W8, I7, N1–N4 | — | won't fix — recorded notes (design §2.5; I7 flagged to the human for a licensing check) | — |

### QA coverage gaps (G-nn → QA-nn)

| Finding | Disposition | Issue |
|---|---|---|
| G-1 / QA-01 (fiscal state machine untested) | fix | #26 |
| G-2 / QA-02 (no e2e) · G-3 / QA-03 (web untested) · G-11 / QA-11 (untranslated RO) | fix | #28 |
| G-4 / QA-04 (pay untested) | fix — explicit test AC: #14 AC6 (full pay-arithmetic matrix incl. 0-row conflict paths) | #14 |
| G-10 / QA-10 (no concurrency tests) | fix — #14 AC1/AC5 (latch-forced settlement race) + #23 AC1/AC2/AC6 (assignment + shift races) | #14, #23 |
| G-5 / QA-05 (tenancy untested) · G-6 / QA-06 (permissions untested) | fix | #27 |
| G-7 / QA-07 (no CI) · G-8 / QA-08 (DB-bound test) | fix | #4 |
| G-9 / QA-09 (untested services) | fix — distributed with a verifiable exit: at epic exit qa diffs G-9's service list (test-plan §11) against the test classes on `develop`; any still-untested service without a §5.6 justification is filed as a new finding before the epic closes | #13–#28 + exit check |
| G-12 / QA-12 (frame content unasserted) | fix | #26 |
| G-13 / QA-13 (stack unbuildable) · F-4 / QA-18 (scripts missing) | fix | #8 |
| G-14 / QA-14 (bridge untested) | fix (pure-logic half, #25 AC4); on-device half deferred (D4) | #25 |

### UX groups (ux.md §10; 87 ids, 86 active — 41 major / 37 minor / 8 trivial; UX-C13 withdrawn into UX-C8 — each active id dispositioned through its group)

| Group | Findings | Disposition | Issue |
|---|---|---|---|
| 1 Waiter error handling | UX-C3, W1, W6, W14, W18, W22, W25 | fix | #29 |
| 2 Customer menu + cart | UX-Q1…Q5, Q9, Q17, Q19, Q20 | fix | #30 |
| 3 Money formatting | UX-C1, B14 | fix | #31 |
| 4 Tokens + focus + combo | UX-C2, C4, C10, C11, B13 (+W5) | fix (ADR-0003) | #32 |
| 5 Service board distance | UX-S1, S2, S3, S5, S6, S8 | fix | #33 |
| 6 Compliance + dead ends | UX-C6, C7, C17, Q10, S7 (+F-10, W6; C8 → #11) | fix (D8) | #34 |
| 7 Waiter board truth | UX-W2, W3, W10, W23 | fix | #35 |
| 8 Fiscal visibility | UX-W7, W8, W9 | fix | #36 |
| 9 Confirmations/undo | UX-W4, W5, W15, S4 | fix | #37 |
| 10 i18n + copy | UX-C5, C14 + §8 corrections | fix | #38 |
| 11 Phone platform fit | UX-C15, C12 | fix | #39 |
| 12 Backoffice correctness | UX-B11, B12, B15 (B10 → #44) | fix | #40 |
| UX-B10 (major defect, design §2.2: reports show raw UTC — early by the full UTC offset, 2 h EET / 3 h EEST) | qa R-S8 | fix — lands WITH #24 (A8 pair; fixing one side shrinks the error without removing it) | #44 (bug sev:major) |
| UX-W14 (major defect, design §2.2: pay sheet shows clamped, submits raw) | qa R-M12 | fix — clamp at source + told via pay.amountCapped; held at major (server caps at the unpaid total, no money moves) | #29 (AC5) |
| 13 Backoffice polish | UX-B1…B8, B16, C9, C18 + B9 tweaks | fix (D10) | #41 |
| 14 Smaller waiter/customer | 17 ids (see #42) | fix | #42 |
| 15 Login a11y | UX-C16 | fix (login stays English — ba) | #43 |
| UX-B9 redesign | — | deferred (D10) | — |
| UX-Q11 (menu search) | — | deferred (D9) | #10 (backlog) |
| UX-Q15 (legal label language) | — | deferred — resolves with the legal content (D8) | — |
| Waiter WS push (from UX-W3/Q2) | — | deferred — poll ships; push is a new api story (recorded in ux.md §10) | — |
| Moving orders between slots (from UX-Q6) | — | deferred — warned recovery path ships (recorded in ux.md §10) | — |
| Split for plain-named tables (from UX-W4/Q13) | — | deferred (D16 — human upheld ba's disposition) | — |

## Story map

Blocked by #4 applies to every implementation story (D1). #8 gates Phase 3/4 verification.

| Issue | Title | Type/Sev | Owner | Estimate | Depends on |
|---|---|---|---|---|---|
| #4 | CI pipeline on PRs | story | devops | S | — (first) |
| #8 | develop builds its own stack | story | devops | S | — (first) |
| #13 | Netopia IPN forgery | bug critical | backend (+devops) | M | #4; human gate: payments |
| #14 | Double-payment race (one task with #15, #5, A17) | bug major | backend | M | #4; schema gate (V3 via #15) |
| #15 | Duplicate order numbers | bug major | backend | rides #14 | #4; schema gate V3 |
| #5 | AMOUNT ignores discount | bug major | backend | rides #14 | #4 |
| #16 | Waiter lines trust client prices | bug major | backend+frontend | M | #4 |
| #17 | CANCELED hides settled money | bug major | backend | S | #4 |
| #18 | Approvals 403→404 | bug major | backend | S | #4 |
| #19 | Bridge tenant binding | bug major | backend | M | #4 |
| #9 | Fiscal re-issue role gate | bug major | backend | S | #4 |
| #11 | Footer tenant identity | bug major | frontend (+V4 BE) | S | #4; schema gate V4 |
| #20 | WebSocket ticket auth | story | backend+frontend | M | #4 |
| #21 | Orders report SQL paging | story | backend | M | #4 |
| #22 | Spring Boot upgrade | story | backend+devops | M | #4; human gate: dependency |
| #23 | API hardening minors | story | backend | M | #4 |
| #24 | Deploy corrections (TZ, concurrency, cache) | story | devops | S | #4 |
| #25 | Bridge code fixes | story | backend | S | #4; device verify deferred (D4) |
| #26 | Fiscal DB-backed tests | story | backend+qa | M | #4 |
| #27 | Tenancy/role test suite | story | backend+qa | L | #4 |
| #28 | Web test foundation | story | frontend+qa | M | #4, #8 |
| #29–#43 | UX groups 1–15 (see register) | story | frontend (BE riders: #35, #36) | S–M each | #4; #37 also #17; #42 also #23; #40 now S (B10 → #44) |
| #44 | Not-paid/Open-tables reports show raw UTC | bug major | frontend | S | #4; lands WITH #24 |
| #45 | Flyway reconciliation runbook | task | devops | S | human gate: release |
| #6 | api/CLAUDE.md + web/CLAUDE.md doc fixes | task | backend | S | — |
| #10 | Customer menu search (deferred, D9) | story | frontend | — | not scheduled |

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
| D8 | 2026-10-24 | Legal pages: routes + shells + not-found ship this epic with a minimal factual placeholder; the legal content comes later from the human as **one Yammer-level set** (answers Q4) | Guests must stop dead-ending on the staff login now; legal text is authored by the human/counsel, not agents | human (Radu) |
| D9 | 2026-10-24 | Customer menu search (UX-Q11) is deferred — recorded, backlog issue filed, not in this epic (answers Q5) | A feature, not a finding; discretionary spend the epic is complete without | human (Radu) |
| D10 | 2026-10-24 | Order Points density (UX-B9): only ux's two cheap tweaks this epic (booleans grouped into one column, fixed "Disc. %" width) — they join UX group 11; the redesign is deferred (answers Q6) | Redesign is a story of its own the human did not ask for | human (Radu) |
| D11 | 2026-10-24 | #13 IPN-verification design approved; ONLINE self-pay STAYS LIVE in the interim (sandbox exposure only) | Gate: payments | human (Radu) |
| D12 | 2026-10-24 | V3 (order-no counter) and V4 (client legal line) schema additions approved | Gate: schema | human (Radu) |
| D13 | 2026-10-24 | #22 Spring Boot 3.2 → 3.5.x approved | Gate: dependency | human (Radu) |
| D14 | 2026-10-24 | #20 WebSocket single-use ticket approved | Gate: architecture | human (Radu) |
| D15 | 2026-10-24 | I9/release: the human will DROP the prod database and reconcile — #45's runbook becomes the drop-and-recreate procedure (document what is lost and the seed path; the GCP action itself stays human) | Gate: release; prod data is sacrificial at this stage | human (Radu) |
| D16 | 2026-10-24 | Q7 split-rename: DEFERRED — the human did not override ba's disposition; ux.md's record stands as written | Gate: requirements | human (Radu) |

## Open questions

| # | Question | Asked to | Status |
|---|---|---|---|
| Q1 | Is there a budget/priority ceiling for Phase 2 — do we implement everything accepted, or stop after critical/major + High-impact? | lead → human | Answered → D3 |
| Q2 | Should `bridge/mobile/` findings that require installing a new APK be scheduled in this epic, given installs are a human gate? | lead → human | Answered → D4 |
| Q3 | AMOUNT payment on a discounted table (bug #5): should it allocate against net (discounted) prices like FULL/PARTIAL (recommended), or stay "taken as is" and be blocked on discounted tables? | lead → human | Answered → D5 |
| Q4 | Legal pages (UX-C6/UX-Q10, major): the six legal links (`/legal/termeni`, `/confidentialitate`, `/livrare`, `/anulare`, `/gdpr`, `/contact`) and any bad QR dead-end on the **staff login form** — the Netopia/ANPC compliance surface of a card-accepting site. Fixing routes + shells + not-found page is in scope (ux specced them, RO/EN copy in ux.md §8); the gate decision is about content only: (i) **who writes the six legal documents** — they are legal text, the human or counsel, not agents; (ii) **one set for Yammer or per-tenant content?** Recommendation: one Yammer-level set supplied by the human; shells ship this epic with a minimal factual placeholder so no guest lands on the login form meanwhile | lead → human | Answered → D8 |
| Q5 | Customer menu search (UX-Q11): the waiter app has search, the customer page does not. It is a new feature, not a defect — the epic's out-of-scope rule says features only when implied by a finding. (a) in scope this epic; (b) **recommended**: defer — record + backlog issue | lead → human | Answered → D9 |
| Q6 | Order Points density (UX-B9): 13 editable columns. (a) **recommended**: ux's two cheap tweaks only this epic, redesign deferred as its own backlog story; (b) full redesign story now | lead → human | Answered → D10 |
| Q7 | Split for plain-named tables (UX-W4/Q13): nothing is broken today — batch-created tables are born `T{n}.1` and the split button is hidden on a manually renamed plain `T12` (the api 400s on the pattern). The question is ADDING capability: splitting a plain `T12` renames it to `T12.1` and creates `T12.2` (safe — all references are by id, printed QRs keep working). ux + architects recommend ADOPT, with the confirm dialog telling the waiter the table is being renamed (a table silently changing name under a guest's printed QR loses trust). I initially deferred it as not finding-implied; given both specialists' recommendation, options: (a) adopt with the rename warning (their recommendation); (b) keep deferred, rule documented | lead → human | Answered → D16 (deferred) |
