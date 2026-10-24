# Test plan — Epic #1 "Codebase audit: UX/UI, architecture and defect findings, then fixes"

Owner: `qa`. Status: Phase 1 (strategy). The per-story scenario tables in §8 are filled in as
`ba` publishes the stories; everything before §8 is settled now and does not depend on them.

---

## 1. What this plan has to achieve

This epic is not a feature. It is a sweep of findings over code that **already runs in
production**, turned into stories whose fixes touch the most dangerous parts of the product:
money arithmetic, the fiscal outbox, tenant isolation, the service day and the role model. The
dominant risk is therefore **not** "the new thing does not work" — a fix is usually small and
obviously right in isolation. The dominant risk is **regression**: a fix that is correct for its
own story and quietly wrong for a bill, a receipt or another tenant.

So this plan is built around three things rather than a flat scenario list:

1. an **invariant register** (§3) — the properties that must hold after *every* fix, whatever the
   story was about;
2. a **verification rule per finding** (§4) — what each fix must prove, at which test level, and
   whether it is automated or verified by hand;
3. a **fixed regression suite with concrete amounts** (§9) — re-run whenever a story touches the
   invariant area it belongs to, so "it still adds up" is a check and not an opinion.

Scope: the whole repository as it stands on `origin/develop` — `api/`, `web/` (backoffice,
waiter, service board, customer page), `db-seeds/`, the documented bridge contract. Out of scope:
executing anything against the bridge hardware or production (§6), and the Android app itself
(`bridge/mobile`) beyond its wire protocol, which is exercised through a stub (§6.4).

## 2. Test basis

A scenario is written against a document, never against observed behaviour — in an audit epic the
observed behaviour is exactly what is under suspicion.

| Source | Used for |
|---|---|
| `CLAUDE.md` §5 ("Yammer rules that are gates") | the invariant register |
| `api/CLAUDE.md` | endpoint contracts, roles per endpoint, payment and fiscal lifecycle, service day |
| `web/CLAUDE.md` | which surface a screen belongs to, i18n rule, shared-table rule |
| `docs/epics/codebase-audit/requirement.md` (`ba`) | the stories and their acceptance criteria |
| `docs/epics/codebase-audit/design.md` (`architect-a` / `architect-b`) | the agreed shape of each fix, and which invariant it is near |
| `docs/epics/codebase-audit/ux.md` (`ux`) | screen states, RO/EN copy, and the §9 accessibility register — the expected result for every UI scenario; §9 is folded into R-U4…R-U8 |
| `api/src/main/resources/db/migration/V1__Initial_schema.sql`, `V2__Seed.sql` | seeded catalogs, roles, the five role names |

**Where the basis contradicts itself, the scenario is blocked and goes back to the author.** Two
such contradictions are already open; both are listed as findings in §11 (F-1, F-2).

## 3. Invariant register

Every story is mapped to the invariants it comes near (§8). The invariants it touches have their
regression block (§9) re-run before sign-off.

| # | Invariant | Where it lives | How it is proven |
|---|---|---|---|
| **I1** | A non-SUPER caller only reaches rows of its own client; a cross-tenant id answers **404, never 403 or 200**; a non-SUPER writer gets `clientId` forced to its own | `security/AccessGuard.java`, each service | L3 two-tenant curl sweep (R-T*) + e2e for whatever the story touches |
| **I2** | Money arithmetic: gross vs net, per-unit discount rounding, PARTIAL line splitting, AMOUNT oldest-first allocation with a split last unit, display re-join of settled fragments, tip never part of a line's value | `OrderService.pay/payFull/payPartial/payAmount`, `util/DiscountMath`, `OrderService.mergeSettledSplits` | L1 unit tests with the §9 amounts + L3 payment calls + L5 e2e on the waiter bill |
| **I3** | A settled order line is never re-split or re-priced | `payPartial` (price-keyed allocation), `payAmount` (`originalPrice` kept on both halves) | L1 + L3: re-pay attempts over already-settled lines |
| **I4** | The fiscal outbox: `NONE / PENDING / SUCCESS / FAILED / UNKNOWN`, **`SUCCESS` terminal**, no automatic retry, `requestId` = payment id, one send per payment, device pinned on first accept, bridge budget 90 s **<** result deadline 180 s | `repository/PaymentRepository` guarded UPDATEs, `BridgeService`, `FiscalResultSweeper`, `PaymentService` | L2 persistence test on the guarded UPDATEs (**new level, §4**) + L3 with the bridge stub (§6.4) |
| **I5** | Service day over dates: every order and payment carries `shift_id`; waiter and service surfaces scope to the open shift and have no date input; a day cannot close while a non-SERVICE point owes money (409 naming the tables); with no shift, the business day starts at 06:00 | `ShiftService`, `OrderService.shiftOf`, waiter endpoints | L1 (partly covered) + L3 + L4 on the waiter surfaces |
| **I6** | Role model: `SUPER / ADMIN / WAITER / SERVICE / WATCHER` plus the anonymous customer; each endpoint reachable by exactly the documented set | `SecurityConfig`, `@PreAuthorize`, service-level checks | L3 role matrix (R-R1), run as a full sweep once per epic and per story that touches auth |
| **I7** | The assignment is the sitting: a round starts at the earliest active assignment, the bill covers the round plus anything older unpaid, leaving or closing is refused with 409 while anything is owed, never-accepted DRAFTs die with the round | `TableRoundService`, `OrderPointAssignmentService` | L1 (covered) + L3 + L5 e2e on two-waiter cases |
| **I8** | Live push: the service board updates without a reload; routing is the caller's assigned SERVICE station, else every SERVICE point of its home location; the registry is in-memory and the api is single-instance | `ws/`, `OrderNotificationService` | L4 two-browser manual check, every time (not automatable cheaply) |
| **I9** | Waiter and customer text goes through `t()` with both RO and EN present; backoffice, login and service board stay English | `core/i18n/translations.ts` | L4 manual in both languages + a `ux` review; no automated text assertions |

**Reverse lookup** — what a diff touches selects the blocks, so neither `qa` nor the author has
to judge it:

| A fix touches | Invariants | Blocks to re-run |
|---|---|---|
| `OrderService` (pay/split/bill), `util/DiscountMath`, `OrderItemEntity` | I2, I3, I5 | R-M, R-F1, R-S1 |
| `BridgeService`, `FiscalResultSweeper`, `PaymentRepository` UPDATEs, `PaymentService`, `bridge/mobile` | I4 | R-F |
| `OnlinePaymentService`, `NetopiaPaymentService`, `/public/payments/**` | I2, I4 | R-O, R-F |
| `security/*`, any service's tenant check, `UserService`, controller annotations | I1, I6 | R-T, R-R |
| `ShiftService`, reports, dashboard, PDF/thermal output | I5 | R-S, R-M7, R-M8 |
| `TableRoundService`, `OrderPointAssignmentService`, `ApprovalService`, `CustomerAccessService` | I7 | R-A, R-M10 |
| `ws/*`, `OrderNotificationService` | I8 | R-P |
| Any waiter/customer template, style or copy; `core/i18n/*` | I9 | R-U |
| `docker-compose.local.yml`, `Dockerfile`, nginx config, `ci.yml` | — | stack up from scratch + one smoke flow per surface |

## 4. Test levels, and what is automated

| Level | What it is | Where it lives | Who runs it |
|---|---|---|---|
| **L1** | Pure unit test, no Spring context (Mockito) | `api/src/test/java/...` | developer in the fix PR; `qa` reviews it covers the story |
| **L2** | **Persistence slice test against a real Postgres** — *does not exist yet*, see G-1 in §11 | `api/src/test/java/...` + a test datasource | `backend-*` once the story that introduces it lands |
| **L3** | API scenario with `curl` against the integration stack: status code, payload, error reason, auth, tenancy | run by hand, commands pasted into the test report | `qa` |
| **L4** | UI flow in a real browser (`playwright` MCP): phone viewport for waiter/customer, desktop for the backoffice; console + network clean, keyboard reachable, both languages where I9 applies | — | `qa` |
| **L5** | Playwright e2e — *`web/e2e/` does not exist yet*, see G-2 | `web/e2e/` | added by `qa` through the normal PR flow |

**The rule for automating (CLAUDE.md §5 point 6):** a scenario gets an automated test when it
protects **money, a permission, tenant isolation or a rule that is easy to break silently**, or
when it is the regression test for a Critical/Major bug — then the test must fail on the
pre-fix code. Everything else is verified by hand: copy and translations, static display,
layout, ordering of a list, anything the framework already guarantees.

Preference between L1/L2/L5 for the same property: **lowest level that can actually fail.** The
fiscal state machine is the clear example — it is implemented as `where`-guarded JPQL UPDATEs, so
a Mockito test proves nothing about it and it needs L2 (see G-1).

## 5. Entry and exit criteria

**Entry to `stage:in-qa`** (otherwise the story goes straight back with a comment):

- `stage:dev-verified`, the PR squashed into `develop`, CI green;
- acceptance criteria present and testable (reviewed by `qa` in Phase 1 — §7);
- for UI findings, `ux.md` carries the screen state and the RO/EN copy the fix produces;
- the PR contains the test the fix deserves under the §4 rule (a bug fix: one test that fails on
  the old code);
- the finding's story says **which invariants of §3 it comes near** — that is what selects the
  regression blocks.

**Exit — `stage:qa-passed`:**

- every acceptance criterion executed on the integration stack built from `origin/develop` **after**
  the fix was merged (never on the author's worktree stack);
- the regression blocks of §9 for the touched invariants: green;
- no open `sev:critical` or `sev:major` against the story; `sev:minor` may remain if logged and
  accepted by the lead;
- evidence in `test-report.md`: scenario id, result, request/response or screenshot, and the
  `develop` commit SHA it ran against;
- for a waiter/customer surface: checked in **RO and EN**;
- for a UI change: `ux` has signed the PR off (that is a merge gate, but `qa` does not pass a
  story whose screen disagrees with `ux.md`).

**Epic exit:** every accepted finding either `stage:qa-passed` or explicitly deferred by the lead;
the full role matrix (R-R1) and the two-tenant sweep (R-T*) re-run once against the final
`develop`; the e2e suite in `web/e2e/` green; `test-report.md` complete.

## 6. Environment

### 6.1 The stack

There is no staging. All execution happens on the **integration stack built from
`origin/develop`** (CLAUDE.md §7):

```bash
git fetch origin && git worktree add .worktrees/develop origin/develop   # once
cd .worktrees/develop && \
COMPOSE_PROJECT_NAME=yammer-develop DB_PORT=6100 GCS_PORT=5100 API_PORT=8700 WEB_PORT=4900 \
docker compose -f docker-compose.local.yml up -d --build
```

- web `http://localhost:4900`, api `http://localhost:8700`, Postgres `localhost:6100`,
  fake GCS `localhost:5100`.
- **Never the base ports** (8080 / 4200 / 5432 / 4443) — those are Radu's own servers.
- The stack is rebuilt (`down -v` then `up -d --build`) before each story's execution run, so a
  result is always attributable to one `develop` SHA. The SHA is recorded per scenario.
- Production is never a test environment, for any reason.

> **Blocker (G-13).** The command above does not work from a `develop` worktree today:
> `docker-compose.local.yml`, `web/nginx.local.conf`, `CLAUDE.md` and `docs/` are **untracked** —
> `git ls-tree --name-only origin/develop` lists only `.github .gitignore api bridge db-seeds web`.
> Until `devops` commits them, the stack can only be started from Radu's own checkout, which
> defeats the "one SHA per result" rule above. Phase 4 cannot start without this.

### 6.2 What the local stack cannot do, and how that is used

| Limitation | Consequence | Turned into a test path |
|---|---|---|
| No bridge connected | CASH/CARD payments stay `PENDING`, the frame queued for the next HELLO | the `PENDING → sweeper → FAILED` path is directly testable (R-F1a); FAILED is then the entry state for the retry scenarios, and the stub connecting inside/outside the deadline gives R-F1b/R-F1c |
| Netopia disabled (`NETOPIA_ENABLED=false`) | ONLINE self-pay returns 503 by design | R-R-ONLINE: the customer page must show a usable error, not a dead button; the browser leg is not executable locally, but the IPN-confirm path **is** — by posting the notify directly (block **R-O**); only the real gateway's own behaviour stays a residual risk (§12) |
| fake-gcs instead of GCS | logo/image upload works, signed URLs differ | image scenarios are display-only, L4 |
| `BRIDGE_API_KEY=local-compose-bridge-key` | a scripted WebSocket client **can** connect as a bridge | the fiscal result paths OK / ERROR / UNKNOWN become testable (§6.4) |

### 6.3 Seed and fixtures

`V2__Seed.sql` gives the global catalogs and the single SUPER operator. Everything tenant-shaped
is built per run. The fixture below is the baseline for every scenario id in §9; it is created
through the API (so it also exercises the create paths) by a script kept with the test report.

| id | Fixture | Content |
|---|---|---|
| **TD-1** | Two tenants | Client **A** "Rendezvous" and client **B** "Glo" — B exists *only* so that every A id can be probed as B (I1) |
| **TD-2** | Users | per client: `admin@<c>`, `waiter1@<c>`, `waiter2@<c>` (two waiters: the concurrency cases), `service@<c>`, `watcher@<c>`; plus the seeded SUPER |
| **TD-3** | Location + points | A: one active location, SERVICE point `S1`, bar `B1`, tables `T1.1`, `T1.2`, `T2.1`; `T2.1` carries `discount_percent = 10.00`; all points accept CASH/CARD/PROTOCOL/PO; B: one location with `T1.1` |
| **TD-4** | Money catalogue | Aqua Panna **11.00**, Prosecco **22.00**, Pizza Margherita **42.00**, Espresso **9.99** — the §9 amounts are computed from exactly these |
| **TD-5** | Peripherals | one `MOBILE` cash register bound to the stub bridge's device id, one TCP printer with an unroutable ip (the "peripheral absent" path) |
| **TD-6** | Empty and large | a location with no points (empty states), and a table with 60 order lines across 12 orders (list/bill/PDF paging and the thermal slip) |

**Dependency:** `CLAUDE.md` §7 refers to `scripts/env-up.sh` and `scripts/seed-tenant.sh`; neither
exists in the repo yet (**F-4**). Until `devops` provides them, `qa` builds TD-1…TD-6 with a local
curl script and keeps it next to the test report — a convenience, unlike the missing compose file
(**G-13**), which is a hard blocker.

### 6.4 The bridge stub

The fiscal invariants (I4) are the highest-value, least-covered area, and all of them turn on
frames coming *back* from a bridge. A small scripted WebSocket client — connect to
`ws://localhost:8700/ws/bridge` with `X-Bridge-Key: local-compose-bridge-key`, announce
`{type:HELLO, deviceId, deviceName}`, then reply to a `RECEIPT` with a chosen
`RECEIPT_RESULT` (`OK` / `ERROR` / `UNKNOWN`), or with nothing at all — makes OK, ERROR, UNKNOWN,
double-result, late-result and silence all reachable without hardware. It also lets a scenario
assert the **content** of the `RECEIPT` frame (`requestId` = payment id, `fiscal:true`, the lines,
the VAT per line, the tip as `exempt:true`) rather than only the resulting status. The stub is
test code, so `qa` owns it; it lands with the first fiscal story, under `web/e2e/fixtures/` or
`scripts/`, wherever `devops` prefers.

## 7. Testability review of the acceptance criteria (Phase 1 duty)

Each story's criteria are reviewed against this checklist before `stage:ready`; concrete
rewrites go to `ba` by message, not as a comment in this file.

1. **Given/When/Then with a named actor role**, not "the user" — I6 means the role *is* the test.
2. **Money criteria carry concrete amounts**, including the expected rounding and which of
   gross / net / tip / discount the asserted number is. "The total is correct" is not a criterion.
3. **An observable result**: a status code, a field value, a named screen state from `ux.md`, a
   stored row. "Works as expected", "is consistent", "is improved" cannot be executed.
4. **A tenancy criterion says 404**, never "is denied" (403 would be a defect, so the word must
   be in the criterion).
5. **Waiter/customer copy is given in RO and EN**, else the I9 check has nothing to compare to.
6. **Time-scoped criteria name the service day**, not a date — including the after-midnight case
   where they differ.
7. **A fiscal criterion names the state** (`PENDING / SUCCESS / FAILED / UNKNOWN`) and whether a
   retry is allowed from it, since the guard is per-state.
8. **A refactor-only finding still needs an observable criterion** — "behaviour is unchanged" is
   testable only if the story says *which* behaviour, i.e. which §9 blocks must stay green.

## 8. Traceability: acceptance criterion → scenarios

Filled in per story once `ba` publishes them. One sub-section per story, in this shape; scenario
ids are `S<issue>-<n>`, and the regression blocks come from the story's invariant column.

### Template

**Story #<n> — <title>** · invariants touched: <I…> · regression blocks: <R…>

| AC | Scenario | Level | Automated | Result |
|---|---|---|---|---|
| AC1 | S<n>-1 happy path, role <R> | L3 + L4 | e2e / manual | |
| AC1 | S<n>-2 boundary: <the edge the AC implies> | L1 | yes | |
| AC2 | S<n>-3 validation: <rule> rejected with <status + reason> | L3 | yes | |
| — | S<n>-4 tenancy: same call as client B → 404 | L3 | yes | |
| — | S<n>-5 roles: WAITER / SERVICE / WATCHER / customer on the same endpoint | L3 | yes | |
| — | S<n>-6 empty + large data | L4 | no | |
| — | S<n>-7 double submit / two actors at once (where the story allows it) | L3 | yes | |
| — | S<n>-8 RO + EN copy (waiter/customer surfaces only) | L4 | no | |

### Stories

*(empty — waiting on `ba`; this section is the only part of the plan that changes per story)*

## 9. Regression suite

Fixed scenarios with fixed numbers, derived from TD-3/TD-4. A story re-runs the blocks of the
invariants it touches; the whole suite runs once at epic exit. Every amount below was computed
from the code as it stands on `develop`, so a diverging result is either the fix working or a
regression — never ambiguity about what the number should be.

### R-M — money (I2, I3)

| id | Scenario | Expected |
|---|---|---|
| **R-M1** | `T1.1`: order 3 × Pizza (126.00). Pay **PARTIAL** 2 × Pizza, CASH | payment `amount = 84.00`; bill: unpaid 1 × 42.00, paid 2 × 42.00; the original line stays unpaid with `quantity = 1`, a new paid line carries `quantity = 2` |
| **R-M2** | `T1.1`: order #1 3 × Pizza (126.00) at 19:05, order #2 2 × Prosecco (44.00) at 19:20 → owed 170.00. Pay **AMOUNT 150.00**, CASH | payment `amount = 150.00`; order #1 fully settled, one Prosecco settled at 22.00, the next unit split into a **paid 2.00** and an **unpaid 20.00**, both carrying `originalPrice = 22.00`; table owes **20.00**; both halves render as partial (tint + half-circle) on the waiter bill and the customer Order view |
| **R-M3** | Continue R-M2: pay **FULL** (20.00), CASH | nothing owed; the Paid view shows **2 × Prosecco at 22.00** as one whole line with no partial marker, and 3 × Pizza at 42.00 — the settled-split re-join |
| **R-M4** | Fresh `T1.1`: 1 × Aqua Panna (11.00). Pay **AMOUNT 20.00** | payment `amount = 11.00` (allocation capped at the unpaid total), nothing owed, no change/overpayment row anywhere — pins the current deliberate behaviour |
| **R-M5** | `T2.1` (discount 10%): 1 × Pizza. Pay **FULL**, CASH | payment `amount = 37.80`, `discount_percent = 10.00`, `discount_amount = 4.20`; nothing owed |
| **R-M6** | `T2.1` (discount 10%): 1 × Pizza. Pay **AMOUNT 37.80**, CASH — **API only, `POST /orders/pay` directly; not reachable from the waiter UI** | **current** behaviour: allocation runs on the gross 42.00, so no full unit is covered — the unit splits into a paid 37.80 and an **unpaid 4.20** and the table still owes 4.20. This is finding **F-5** / bug #5. **D5 has now decided it** (option (a)): AMOUNT allocates against the **net** discounted unit prices like FULL and PARTIAL, so after the fix paying 37.80 settles the table completely, the payment records `discount_percent = 10.00` / `discount_amount = 4.20`, and nothing is owed. Until then the row documents the defect. **The waiter UI hides AMOUNT mode on a discounted table** (`waiter-table-detail-page.html:94` renders the button only `@if (!(bill()?.discountPercent))`), so the only route in is the API — which any authenticated user of the tenant can call, since the guard is a template conditional and not a server rule. That bounds the defect's reach and is why this row is API-level; see the note under the table |
| **R-M7** | `T1.1`: 3 × Pizza (126.00). Pay **FULL** CARD with **tip 10.00** | payment `amount = 126.00`, `tip = 10.00`; Payments report total 136.00; the waiter cash-up shows card 126.00 and tips 10.00 separately; the `RECEIPT` frame carries 3 priced lines plus one `exempt:true` line of 10.00 |
| **R-M8** | `T1.1`: 2 × Prosecco (44.00). Pay **FULL PROTOCOL** | `status = SUCCESS`, `fiscal_status = NONE`, no `RECEIPT` frame; absent from the waiter Payments page; present in the Not-paid report with its two product lines and in the final report's protocol column; **not** in the waiter's own total |
| **R-M9** | `T2.1` (discount 10%): 3 × Espresso (9.99). Pay **FULL**, CASH | per-unit discount 8.99 (9.99 × 0.90 = 8.991 → HALF_UP) → payment `amount = 26.97`, `discount_amount = 3.00` |
| **R-M10** | After R-M1, attempt **PARTIAL** on the already-paid 2 × Pizza, and **AMOUNT 200.00** on the fully settled table | both refused (400 "Nothing to pay" / quantity exceeds unpaid); **no settled line is re-split or re-priced** (I3) |
| **R-M11** | On `T1.1` with 3 × Pizza: in the waiter pay sheet choose PARTIAL, tick 2 × Pizza, enter a 10.00 tip, and make the call fail (payment type not accepted at the point, or the api stopped) | the error is shown **and the sheet keeps mode, ticked items and tip** so the waiter re-submits instead of re-entering. Current behaviour per `ux.md` UX-C3: a declined payment closes the sheet and discards all three. Money-adjacent because the re-entry is where a waiter charges the wrong quantity — and because an operator who believes the first attempt partly landed may double-charge. Owned as a UX finding; this block exists so the fix is verified against the bill, not only against the screen |
| **R-M12** | `T1.1` with 1 × Aqua Panna (11.00): in the pay sheet choose AMOUNT and type `500` | the screen and the request must not disagree. **Current** behaviour (`ux` UX-W14, verified): the summary clamps for display to `11.00` (`waiter-table-detail-page.ts:238`) while the request sends the raw `500` (`:563`); the server then caps the allocation at the unpaid total, so the *recorded* payment is 11.00 and no money is lost — the server cap is the only thing between this and an overpayment. Assert both halves: the submitted payload, and that nothing is submittable while display and field diverge once the fix lands. Pairs with R-M4, which pins the server-side cap |

### R-F — fiscal outbox (I4)

| id | Scenario | Expected |
|---|---|---|
| **R-F1a** | CASH payment, no bridge connected for the whole deadline | `fiscal_status = PENDING`, `requestId` = the payment id; the waiter Payments page and the backoffice Fiscal column both show PENDING with no Retry; the frame is **queued**, not sent (`BridgeService.waiting`, `BridgeService.java:98,133-145`), so "no auto-retry" does not mean "one wire attempt"; after the 180 s deadline the sweeper moves the payment to **FAILED** and Retry appears |
| **R-F1b** | Same, but the stub bridge sends HELLO **before** the deadline | the queued RECEIPT is delivered on registration (`onDeviceRegistered`) and the payment resolves from the stub's `RECEIPT_RESULT` — PENDING in the meantime is correct, not a defect |
| **R-F1c** | Stub connects **after** the deadline | the expired frame is dropped and never printed late; the payment stays FAILED |
| **R-F2** | Retry from each state (stub connected) | `FAILED` → re-armed to PENDING; `PENDING`, `SUCCESS`, `UNKNOWN`, `NONE` → **409**, status untouched |
| **R-F3** | Stub replies `RECEIPT_RESULT OK` with a receipt number | `SUCCESS` + receipt number shown; `fiscal_device` pinned to the stub's device id |
| **R-F4** | Stub replies `OK`, then `ERROR` and `UNKNOWN` for the same `requestId` | stays **SUCCESS** with the original receipt number — `SUCCESS` is terminal |
| **R-F5** | Stub replies `UNKNOWN` | `UNKNOWN`; waiter page says to see a manager (no Retry for a waiter); `resolve-unknown {printed:true}` → SUCCESS; from any other state → **409** |
| **R-F6** | Stub replies `UNKNOWN`, operator resolves "not printed", then retries | `FAILED` + `fiscal_reprint_authorized` set; the next `RECEIPT` frame carries `clearIntent` |
| **R-F7** | Two stub bridges connected, TCP-configured register | the receipt is sent to **exactly one** of them and pinned; a MOBILE register sends only to its own `device_id` |
| **R-F8** | Budget invariant | configuration check: `bridge.fiscal-result-timeout-seconds` (180) stays strictly above the bridge's 90 s job budget. The two numbers live in different codebases, so each side pins its own: an api config test asserting the `application.yml` default (so a yml edit trips it) and a bridge unit test asserting its constant. Any story changing one fails this block unless both move together |

### R-T — tenancy (I1)

| id | Scenario | Expected |
|---|---|---|
| **R-T1** | As `admin@B`, call every client-A id: `/locations/{A}`, `/order-points?locationId={A}`, `/orders?orderPointId={A}`, `/orders/bill`, `/orders/pay`, `/shifts?locationId={A}`, `/payments?locationId={A}`, `/reports/*?locationId={A}`, `/products`, `/menu/menus/{A}/tree`, `/integrations`, `/users/{A-user}`, `/users/{A-user}/qr`, `/locations/{A}/qr` | **404** every time — never 403, never a payload, never an empty 200 that implies existence |
| **R-T1b** | As `waiter@B` (and `admin@B`), the **approval** endpoints with client-A ids: `POST /approvals/customers/{A-session}`, `POST /approvals/orders/{A-order}`, `PUT /order-points/{A}/self-order-mode` | **404.** Current behaviour (architects' **A7**, verified): `ApprovalService` loads by id and calls `requireAssigned` first, which throws **403 "Not assigned to this table"** (`:106`, `:122`, `:143`, `:155-157`) with no tenant guard ahead of it — so a cross-tenant id is confirmed to exist. A direct I1 break; these three endpoints were missing from R-T1 until the architects found it |
| **R-T1c** | As `admin@B`, `GET /bridge/devices` while a client-A bridge is connected | must not list A's device. Current behaviour (**B-T1**): the endpoint lists **every** tenant's connected phones to any ADMIN, and a TCP-register receipt routes to *any* connected bridge, so A's fiscal frames can be handed to B's phone. Tenancy **and** fiscal routing — re-run R-F7 together with this one |
| **R-T2** | As `admin@B`, create a user / location / product sending `clientId` = A | the row is forced onto B; A never grows a row |
| **R-T3** | As `admin@B`, try to grant SUPER or edit a SUPER account | refused; SUPER accounts invisible in the list |
| **R-T4** | As `waiter1@B`, assign to an A table and pay on it | 404 at the assign step; no payment, no shift opened on A's location |
| **R-T5** | Public endpoints with an A order-point id, unauthenticated | by design no tenant check: `/public/order-points/{A}` serves name, clientId and the menu — assert it leaks **nothing beyond that** (no waiter names, no bill, no other table), and the bill endpoint still demands an APPROVED token (403 otherwise) |

### R-S — service day (I5)

| id | Scenario | Expected |
|---|---|---|
| **R-S1** | No open shift; a waiter takes an order | a shift is opened implicitly, attributed to that waiter; the order and any payment carry its `shift_id`; a pure read (`/shifts/current`) opens nothing |
| **R-S2** | Close the day while `T1.1` owes money | **409** naming `T1.1`; the Shifts page lists the blockers above the table and re-checks them |
| **R-S3** | Close the day with only `S1` (SERVICE) assigned | allowed — service stations never block |
| **R-S4** | Close the day, then look at the waiter's Payments and Statistics | the settled money does not reappear; the pages show the window the open day defines, and carry **no date input anywhere** |
| **R-S5** | Order at 00:30 with the evening's day still open | it lands in the open day; the dashboard for that day includes it |
| **R-S6** | Order at 00:30 with no day open at all | it lands in the business day that started at 06:00 the previous morning |
| **R-S7** | Dashboard and final report by `shiftId` vs by `from`+`to` over the same trade | the same totals, from both parameter shapes |
| **R-S8** | One PROTOCOL payment taken at a known wall-clock time, then read on the **dashboard**, the **Shifts** page, the **Not-paid report** and the **Open-tables report** | the same timestamp on all four. **Current** behaviour (`ux` UX-B10, verified): `shared/tz.ts` is the single source of truth — the api returns zone-less strings recorded in UTC, so they must be parsed as UTC and rendered in `Europe/Bucharest`, which `roDate` does. But `not-paid-report-page.html:76` and `open-tables-report-page.html:55` use Angular's `\| date: 'dd.MM.yyyy HH:mm'`, which reads a zone-less string as **local** — so those two render every event **one hour early in winter, three in summer**. A manager reconciling the Not-paid report against the service day reads two clocks for one payment. A report-correctness defect, not a styling one, so it belongs in the I5 block |
| **R-S9** | The same payment on **printed** output: the shift cash-up thermal slip and the waiter PDF statement, against the dashboard | the same wall-clock time everywhere. Current behaviour (architects' **A8**, verified: no `TZ` set in `deploy.yml`, `api/Dockerfile` or `application.yml`): the server renders `LocalDateTime.now()` into slips and PDFs, so **printed** times run 2–3 h behind Romania. Note this is the opposite direction from R-S8's two client-side reports, which run 1–3 h *early* — so today a manager can hold three different times for one payment. Both fixes must land before this block passes |

### R-R — roles and permissions (I6)

**R-R1** — the full matrix. For each of SUPER / ADMIN / WAITER / SERVICE / WATCHER / anonymous
customer, every endpoint group in `api/CLAUDE.md` is called with a valid token for that role and
asserted against the documented set: allowed → 2xx; not allowed → **403** when the row is in the
caller's own tenant, **404** when the id belongs to another one (I1 wins over I6); unauthenticated
→ 401 everywhere except the `permitAll` set in `SecurityConfig`: `GET /`, `/auth/login`,
`/auth/login/qr`, `/actuator/**` (only `health` and `info` are exposed), `/error`,
`GET /clients/*/logo`, `GET /qr-templates/*/image`, `/public/**` and `/ws/**` (whose handshake
authenticates from `?token=`). Particular attention to the roles no one exercises daily:

- **WATCHER**: can reach the dashboard read-only and *nothing* that writes; cannot reach the
  waiter or service surfaces; `/watcher` is its only home.
- **SERVICE**: the board and status changes, not payments, not reports.
- **WAITER**: direct table access is **tenant-scoped, not assignment-scoped** — any authenticated
  user of the tenant may read and pay a table through `GET /orders?orderPointId=`,
  `GET /orders/bill` and `POST /orders/pay` (`AccessGuard.requireAccessibleOrderPoint` is the only
  check; `api/CLAUDE.md` documents the ordering endpoints as "any authenticated user,
  tenant-checked"), and R-A1 depends on exactly that for a second waiter joining a table. What
  scopes to assignments is the waiter's own **list** surfaces: `/order-points/orders`,
  `/order-points/payments`, `/order-points/my-stats`, `/approvals`, and
  `PUT /order-points/{id}/self-order-mode`. Cross-tenant → 404.
- **WAITER, fiscal**: the waiter's `POST /order-points/payments/{id}/retry-fiscal` is reachable;
  the backoffice `POST /payments/{id}/retry-fiscal` and `resolve-unknown` are ADMIN/SUPER
  (`PaymentController` carries `@PreAuthorize("hasAnyRole('ADMIN','SUPER')")` at class level — the
  two retry endpoints are different doors to the same `PaymentService.retryFiscal`, so a scenario
  must name which one it calls). That service method is tenant-scoped but **not**
  assignment-scoped, so a waiter retrying a colleague's FAILED payment in the same tenant
  succeeds today — and **A15 decided to keep it and document it**: the re-arm is FAILED-only and
  guarded, the bridge dedups by `requestId`, and scoping it would break shift handovers. The
  scenario therefore asserts waiter-to-waiter retry as **correct**, and Phase 4 files no bug for
  it. The *role* hole on the same door is a separate finding and is being fixed — next bullet.
- **Any authenticated role can re-issue a fiscal receipt.** `OrderPointAssignmentController` is
  annotated `@PreAuthorize("isAuthenticated()")` at class level
  (`OrderPointAssignmentController.java:30`) and its `retryFiscal` delegates straight to
  `PaymentService.retryFiscal` (`:91-95`, `OrderPointAssignmentService.java:155-157`), whose only
  check is `AccessGuard.requireAccessibleOrderPoint`. So **SERVICE and WATCHER can also trigger a
  fiscal re-print** for any payment in their tenant. For WATCHER that is a write action by a role
  documented as read-only, which is a role-boundary breach rather than a scoping nit — the matrix
  asserts it per role (scenario **R-R1f**) and it is wider than A15's waiter-vs-waiter framing.
  Raised to the architects; graded against the `sev:critical` clause "a role reaches something it
  must not" if that definition lands.
- **anonymous customer**: `/public/**` only, and inside it only the own session's bill.

**R-R3** — the **global** catalogs are not tenant-scoped and must not be probed as if they were:
`/roles`, `/order-point-types`, `/payment-types`, `/self-pay-types`, `/vat-types` and
`/qr-templates` (V1/V2 seed; a location references a template) — list for any authenticated user,
**writes SUPER-only**, and an ADMIN of either client sees the same rows. `GET /qr-templates/*/image`
is public.

**R-R2** — login: every role can sign in with a password (see F-1), a user with no role gets the
same 401 as bad credentials, 10 failures in 15 min → 429 on both the IP and the username key,
QR login is not role-restricted, and a reset QR token invalidates the previous one.

### R-O — online self-pay / Netopia (I2, I4)

The gateway is disabled in the stack, so the browser leg is not executable; the money-critical leg
— the server-to-server IPN, which is the only thing that creates the order and the payment — is,
by posting to `/public/payments/netopia/notify` directly. The architects' register carries a
**critical** finding here (the notify endpoint is unauthenticated and the amount check is
skippable), so this block exists before any story does.

Two details that decide how that finding is graded, both verified in
`OnlinePaymentService.handleIpn` (`:111-153`): the amount check is `if (paidAmount != null && …)`,
so an IPN that simply **omits** the amount is never checked against the intent — the bypass needs
no matching value, only an absent field; and the only thing identifying the payer is the intent
reference, which the customer's **own browser already holds** (the return page polls
`/public/payments/{ref}/status` with it). So this is not "an attacker who guesses a UUID" — it is
**any customer confirming their own unpaid order**, which is why R-O5b asserts it explicitly
rather than leaving it inside R-O5.

| id | Scenario | Expected |
|---|---|---|
| **R-O1** | Customer orders at `T3.1` (self-pay ONLINE) with the gateway disabled | 503 by design; the customer page shows a usable message, not a dead button; **no** order, **no** payment, and the cart survives |
| **R-O2** | Intent created (gateway stubbed/forced), no IPN yet | an `online_payment` intent is PENDING; no order, no payment, nothing on the bill, kanban or any report; `GET /public/payments/{ref}/status` reports pending |
| **R-O3** | Confirmed IPN arrives once | the order **and** the ONLINE payment are created exactly once; the order enters the normal flow (numbered, ORDERED, pushed to the board); `fiscal_status` PENDING like any other fiscalized payment |
| **R-O4** | The same confirmed IPN replayed (gateways retry) | "already processed" — **no second order, no second payment, no second receipt** |
| **R-O5** | Confirmed IPN that **states** an amount not matching the intent | the intent fails; no order, no payment |
| **R-O5b** | Confirmed IPN (`payment.status = 3`) for a real PENDING reference with the **amount field absent**, posted unauthenticated with no gateway involved | **current behaviour: accepted** — the order and the ONLINE payment are created, i.e. a customer who has their own reference can settle their own bill for free (`OnlinePaymentService.java:135-141`). This is the reproduction for the architects' critical finding; after the authenticity story it must be rejected, and R-O3 must still pass with a genuine IPN |
| **R-O6** | IPN for an unknown / already-expired reference | rejected; nothing created |
| **R-O7** | Intent left untouched for > 30 min | expired by the scheduler; a late IPN for it creates nothing |
| **R-O8** | Post-fix (authenticity story) | an IPN that fails signature/authenticity verification is rejected, and R-O3 still passes with a genuine one |

### R-A — rounds, assignment, approvals (I7)

| id | Scenario | Expected |
|---|---|---|
| **R-A1** | `waiter1` assigns `T1.1` at 19:00, `waiter2` joins at 20:00 | the round starts at 19:00; the bill covers both waiters' orders |
| **R-A2** | `waiter1` leaves while 20.00 is owed; then after it is settled | 409 first, allowed second; the last waiter out ends the round |
| **R-A3** | `POST /close` on a table that owes | 409; on a settled one, every assignment is dropped and never-accepted DRAFTs are deleted |
| **R-A4** | Two waiters assign the same single-user point at once; then the same `POST /assign` twice | one wins, the other gets 409; the repeat is idempotent, not a second row |
| **R-A5** | Two devices pay the same bill at the same time (FULL + FULL, FULL + AMOUNT) | the table is not settled twice: the second call sees nothing (or only the remainder) to pay, and no line carries two payment ids. **Expected to fail on today's code** — there is no locking in the api (no `@Version`, no `FOR UPDATE`), the architects' finding **A2**; a red result here during early execution is that finding, not a new bug, until the fix story lands |
| **R-A6** | Customer joins by QR under CONFIRM; orders; waiter approves / denies | DRAFT invisible to bill, kanban, waiter order list and reports; on approve it gets the next per-client number and goes ORDERED and appears on the board; on deny it is gone |
| **R-A7** | Customer re-scans after a browser restart, inside the round and after it ended | inside: resumes APPROVED from the stored token; after: a fresh PENDING join |

### R-P — live push (I8)

**R-P1** — two browsers: a SERVICE user on the board, a waiter placing an order. The card appears
without a reload, within a couple of seconds; the status change moves it in the other direction;
with the socket killed, polling still refreshes it; on reconnect there are no duplicate cards.
Routing: a station-assigned SERVICE user sees only its station's points; an unassigned one sees
every SERVICE point of its home location.

### R-U — surfaces (I9 and the house rules)

**R-U1** — waiter and customer pages in **RO** and **EN**: no raw `t('…')` key on screen, no
English fallback inside a Romanian page, and the strings match `ux.md`. Backoffice, login and
service board stay English.
**R-U2** — every table on a touched backoffice page still uses the shared `_crud-table` panel and
looks identical to the Clients page; no native `<select>` has appeared; no hard-coded hex.
**R-U3** — phone viewport (390 × 844) for waiter and customer, desktop for the backoffice: no
horizontal scroll, sticky footers reachable, console and network clean.

**R-U4 — accessible names on the bilingual surfaces, automatable (L5/L1).** `ux` supplied the
check that catches every English-only accessible name on the waiter and customer surfaces, and it
is the cheap half of I9 that G-11 cannot see (G-11 compares dictionary *values*; this catches
attributes that never went through `t()` at all):

```
grep -rnE '(aria-label|title|alt|placeholder)="[A-Za-z][^"{}]*"' \
  web/src/app/features/waiter web/src/app/features/customer | grep -v "t('"
grep -rn "toast.show(\`" web/src/app/features/waiter          # template-literal toast bypassing t()
```

Eleven hits today plus one template-literal toast. Worth wiring as a test rather than a habit —
it is a grep, it protects a documented rule, and it fails on the pre-fix tree.

**R-U5 — keyboard and focus (L4, per touched surface).** From `ux.md` §9: `combo-box` — Tab
reaches the trigger, Enter/Space opens, Arrows move, Enter selects, Escape closes **and returns
focus to the trigger**; backoffice report rows — an order is selectable without a mouse; waiter
order page — no `role="button"` span nested inside a `<button>`; and every interactive element
shows a ring on `:focus-visible` (today `:focus-visible` appears in 2 files, both for the
checkbox). A story touching any of these re-runs this block on its own surface.

**R-U6 — target size (L4, measured, not eyeballed).** At the phone viewport, in devtools: floor
**44 px** on waiter and customer surfaces, 24 px absolute minimum anywhere. Known failures to
re-measure after a fix: customer steppers 24 px, waiter split 30 px / close 32 px / shell 36 px,
customer legal links 7 px.

**R-U7 — name/role/value and live regions (L4).** Every dialog carries `aria-labelledby`; icon
buttons on the service board include the order number in their accessible name; the customer logo
is not `alt="logo"`; `aria-expanded` is present where a control expands; the toast carries
`role="status" aria-live="polite"` and does not overlap the sticky action bar.

**R-U8 — contrast (L4, measured with a tool).** White on `--danger` **fails** AA for normal text
(approvals badge, `confirm-dialog` danger button); `--muted` and `--primary` on white pass. The
*ratio* is asserted from a contrast tool at test time, not from a number quoted in a spec — `ux`
records 3.3:1 for white on `#ea4d4d` and my own sRGB calculation gives ≈3.7:1; both fail the 4.5
threshold, so the finding stands either way, but a scenario must not assert a figure the two
sources disagree on. Flagged back to `ux`.

**Deliberate exception, not a defect:** the login screen's `user-scalable=no` is an accepted POS
decision recorded in `ux.md` §9 (WCAG 1.4.4). No bug is filed for it, and a Phase 4 run that
notices it says "accepted exception" rather than reopening it.

**Missing screen states — new scenarios, not regressions.** These states do not exist today, so
they are the expected result of their stories rather than something to protect: no loading state
on any of the 20 backoffice pages (UX-B1); no loading state and no empty column on the service
board, where `loading()` is maintained but never rendered (UX-S3); no "no matches" state on the
waiter product search (UX-W17); `payments.empty` serving both "no payments" and "none with this
filter" (UX-W9, needs `payments.emptyFiltered`); and the error-channel split in UX-C3 — a loader
failure keeps the page-level error, an action failure is a toast **plus** an inline line inside the
sheet. Each gets an L4 scenario per surface, in RO and EN.

## 10. Defect management

A bug becomes a GitHub issue: `bug` + `sev:*` + the owning role label, body `Part of #<story>`,
the engineer whose PR introduced it, the environment and `develop` SHA, preconditions, exact
steps, expected vs actual, evidence. The story goes to `stage:reopened` and the developer gets a
message with the number. Every Critical or Major bug also gets an automated test (L1/L2/L5) that
fails on the pre-fix code.

| Severity | In this product |
|---|---|
| **critical** | money is wrong or lost; a fiscal receipt is double-printed or silently not printed; one tenant sees another's data; a role reaches something it must not; a surface is unusable with no workaround |
| **major** | a documented rule is broken with a workaround (a 409 guard that does not fire, a day that closes over an open table, a report that disagrees with the payments it sums); a screen state that contradicts `ux.md` and misleads the user |
| **minor** | wrong or untranslated copy, a layout or ordering defect, a confusing but harmless state |
| **trivial** | cosmetic, no user consequence |

## 11. Coverage baseline and gaps (this epic's QA findings)

What the repository protects today, as of `develop` @ `628c193`. Ten test classes in
`api/src/test`, all pure Mockito/static unit tests, plus one Spring smoke test; **one** web spec;
no e2e; no CI.

Measured, not inferred — `cd api && mvn -o test` in a clean worktree:

```
Tests run: 26, Failures: 0, Errors: 1, Skipped: 0
YammerApplicationTests.contextLoads » IllegalState Failed to load ApplicationContext
  Caused by: Connection to localhost:5432 refused
```

and `cd web && npx ng test --watch=false` → `Test Files 1 passed (1) · Tests 1 passed (1)`.
So **the api build is red on any machine without a Postgres on the base port** (G-8) — including
the `ci.yml` that does not exist yet (G-7). 26 api tests and 1 web test is the whole net under a
~39.5k-LOC product with 36 api services, 56 `@PreAuthorize` rules, 42 web components and 28 web
services.

Covered: the service-day cash-up and close guard (`ShiftServiceTest`, 6 tests), table rounds
(`TableRoundServiceTest`, 6), the display-only re-join of settled splits
(`OrderServiceBillMergeTest`, 4), per-unit discount rounding (`DiscountMathTest`, 2), split-slot
naming (`OrderPointServiceSplitTest`, 2), and report/PDF rendering
(`DashboardReportServiceTest`, `NotPaidReportServiceTest`, `WaiterStatementPdfServiceTest`,
`QrPdfServiceTest`).

The gaps below are offered to `ba` as findings in their own right.

**Canonical ids: `G-nn` for coverage gaps, `F-nn` for code and documentation findings, as numbered
in this document.** Nothing is renumbered once published; a new finding takes the next free number.
Current totals: **14 gaps (G-1…G-14) and 10 findings (F-1…F-10) = 24 items.**

**The `QA-nn` alias, stated in full and frozen.** It maps positionally onto the ids below, and
agrees exactly with the disposition register in `requirement.md`:

| `QA-nn` | Canonical | `QA-nn` | Canonical |
|---|---|---|---|
| QA-01 … QA-14 | **G-1 … G-14**, in numeric order | QA-15 … QA-21 | **F-1 … F-7**, in numeric order |
| QA-22 | **F-8** | — | **F-9, F-10** have no alias (added later) |

**The alias is closed: nothing new gets a `QA-nn`.** New findings are referenced by their canonical
`G-nn`/`F-nn` only, here and in every register. If this table and `requirement.md`'s ever disagree,
`requirement.md` wins — `ba` owns the consolidated register and the exit diff runs from it.

An earlier hand-off message to `architect-a` used `QA-01…QA-18` for a per-service split of G-9 and
a different ordering. **Those ids are superseded — ignore them.** The architects confirmed they
folded the findings in by content, not by number, so nothing indexes them any more. If a register
needs the per-service granularity back, take it from G-9's own list rather than reviving `QA-nn`.

| # | Gap | Evidence | Why it matters |
|---|---|---|---|
| **G-1** | **The fiscal state machine has no test and cannot have one at the current test level.** Every transition — `SUCCESS` terminal, ERROR only from PENDING, retry only from FAILED, resolve only from UNKNOWN, first-writer-wins device pinning — is a `where` clause in a JPQL UPDATE. The only test dependency is `spring-boot-starter-test`; there is no Testcontainers, no embedded DB, no `src/test/resources`, no `@DataJpaTest` | `api/src/main/java/com/yammer/repository/PaymentRepository.java:71-128`; `api/pom.xml` test deps; `ls api/src/test/resources` → absent | the strongest money/fiscal invariant in the product is enforced by text nothing executes in tests |
| **G-2** | **No e2e layer at all**: `web/e2e/` does not exist and Playwright is not a dependency of `web/package.json` | `web/package.json` devDependencies | nothing guards a money, permission or tenancy flow end to end; every such check is manual today |
| **G-3** | **The web surfaces are untested**: one spec, `app.spec.ts`, asserting the root component is truthy | `web/src/app/app.spec.ts` | the waiter bill, the pay sheet, the split rendering, the i18n dictionaries and the role-based home all carry real logic with zero tests |
| **G-4** | **`OrderService.pay` — PARTIAL and AMOUNT allocation — has no test.** No test constructs an `OrderService`; the one OrderService test exercises the static display-only merge | `grep -rl "new OrderService" api/src/test` → nothing; `OrderService.java:497` (`payPartial`), `:565` (`payAmount`) | the line splitting and the oldest-first amount allocation, including the partially-paid unit, are the most dangerous arithmetic in the product |
| **G-5** | **Tenant isolation has no test.** `AccessGuard` appears in tests only as a mock that is told to succeed; the 404-not-403 rule and the forced `clientId` on non-SUPER writes are unexercised | `AccessGuard.java`; `mock(AccessGuard.class)` in 5 test classes | an I1 regression would ship silently |
| **G-6** | **No permission test anywhere** — no `@PreAuthorize` rule, no `SecurityConfig` URL rule, nothing about WATCHER or SERVICE, is asserted | `api/src/test` has no web-layer (MockMvc) test at all | I6 is documentation only |
| **G-7** | **No CI.** `CLAUDE.md` §2 and §3 promise `ci.yml` on pull requests (build, tests, release gate); `.github/workflows/` contains only `deploy.yml` | `ls .github/workflows/` | "green CI" is a merge gate with nothing behind it; `mvn test` and `npm test` only run when someone remembers |
| **G-8** | **The only Spring test needs a live database**, so it cannot run unattended (and would be the first thing to break a CI job) | `YammerApplicationTests`; `api/CLAUDE.md` "the context-load smoke test needs the DB up" | blocks G-7; the fix for G-1 (a test datasource) fixes this too |
| **G-9** | **`BridgeService` (523 lines), `OnlinePaymentService`, `ApprovalService`, `CustomerAccessService`, `OrderPointAssignmentService` (445 lines), `UserService`, `JwtService`, `LoginAttemptService`, `FiscalResultSweeper` have no tests** — including the single-user-point 409, the Netopia IPN-only order creation, the DRAFT approval flow and the login throttle | `api/src/test/.../service/` holds 8 classes against 36 services | each is an invariant area with no net |
| **G-10** | **Nothing covers concurrency**: two waiters on one point, two devices paying one bill, a duplicated POST. The guards exist (unique index on the open shift, `allow_multiple_users`, the guarded UPDATEs) but no test drives two actors | — | these are exactly the field failures a POS sees |
| **G-11** | **The i18n dictionaries are not checked.** The RO dictionary is typed against EN, so a *missing* key fails the build; an **untranslated** value (English text left in the RO map) does not | `web/src/app/core/i18n/translations.ts` | a one-line test (no RO value identical to its EN value, allow-listed exceptions) would hold I9 |
| **G-12** | **No test asserts a sent bridge frame's content** — `requestId` = payment id, `fiscal:true`, the lines, per-line VAT, the tip as `exempt:true` | `BridgeService.java` | the receipt could be wrong in a way every status check still calls SUCCESS |
| **G-13** | **The integration stack cannot be built from `develop`**: `docker-compose.local.yml`, `web/nginx.local.conf`, `CLAUDE.md` and `docs/` are untracked and absent from `origin/develop`; `scripts/` does not exist either (F-4) | `git ls-tree --name-only origin/develop` → `.github .gitignore api bridge db-seeds web`; `git status --short` in the main checkout | **Phase 4 has no environment.** Every "tested on a stack built from `develop`" claim in CLAUDE.md §7 and in this plan is unexecutable until `devops` commits them |
| **G-14** | **`bridge/mobile` has no tests at all** — 19 Kotlin files, no test source set — including the exactly-once intent journal and the 90 s job budget that I4 depends on | `find bridge -name '*Test*'` → nothing | the journal is the only thing standing between a dropped socket and a double-printed receipt; value here is in pure-logic unit tests, since installing the APK is a human gate |

### Findings found while reading, outside test code

| # | Finding | Evidence |
|---|---|---|
| **F-1** | `api/CLAUDE.md` says "**Login is restricted to ADMIN and SUPER** — other roles get the same 401 … (see `AuthService.ALLOWED_LOGIN_ROLES`)". That constant does not exist; `AuthService.login` admits **any** user with at least one role, which is what `web/CLAUDE.md` describes. Stale documentation on an auth rule — the test basis contradicts itself about who may sign in | `api/src/main/java/com/yammer/service/AuthService.java:18-47`; `grep -rn ALLOWED_LOGIN_ROLES api/src/main` → nothing |
| **F-2** | `api/CLAUDE.md` contains both "**Every order and payment is stamped with `shift_id` (V44)**" and, a few lines later, "Orders and payments are **NOT** stamped with a shift id: the window over `created_at` is the scope", then describes `windowFor(locationId)`. The code stamps `shift_id` (`OrderService.shiftOf`) — so the second paragraph and the `windowFor` description are the stale half. Directly ambiguous for every I5 scenario | `api/CLAUDE.md` service-day section; `OrderService.java:628` |
| **F-3** | `AuthController.clientIp` trusts the first `X-Forwarded-For` hop unconditionally, so a caller can rotate the header to defeat the IP throttle. For `/auth/login` the username key still throttles; for `/auth/login/qr` **only** the IP key is used, so the documented QR-login throttle is bypassable (practical impact limited by the 122-bit token) | `AuthController.java:50-77` |
| **F-4** | `CLAUDE.md` §7 documents `scripts/env-up.sh` and `scripts/seed-tenant.sh`; there is no `scripts/` directory | repo root |
| **F-5** | **AMOUNT mode ignores the order point's discount.** `payFull`/`payPartial` charge the net price; `payAmount` allocates against the gross `lineTotal` and records `discount = null`. On `T2.1` (10%), a bill of one 42.00 pizza is 37.80 under FULL but paying AMOUNT 37.80 covers no whole unit, splits the unit, and leaves 4.20 owed — the same customer pays the gross price and the table looks unsettled. Candidate `sev:major` money defect; needs a product decision (`ba` + architects) rather than a QA verdict | `OrderService.java:471-473` and `:565-578` (`createPayment(request, remaining, remaining, null, me)`) |
| **F-6** | `OrderService.createPayment` resolves the payment type from the repository twice into `typeName` and `type` | `OrderService.java:636-653` — trivial |
| **F-7** | `api/CLAUDE.md` says the RECEIPT is built and "sends it ONCE" with "no automatic retry ever", and does not mention that a frame for an offline bridge is **queued** and delivered on the next HELLO inside the 180 s deadline (expired ones dropped). Both statements are true of different things, but the doc as written makes R-F1's expected result ambiguous — found while an architect review corrected this plan's own first draft | `BridgeService.java:92-97` (the `waiting` queue), `:133-145`, `:169-183` (`onDeviceRegistered`) |
| **F-8** | **Every authenticated role can trigger a fiscal re-issue.** `OrderPointAssignmentController` is `@PreAuthorize("isAuthenticated()")` at class level, and its `retryFiscal` delegates to `PaymentService.retryFiscal`, whose only check is the tenant guard. So SERVICE and WATCHER — not just a waiter on someone else's table — can re-print a receipt for any payment in their tenant. WATCHER is documented as read-only (`web/.../watcher-page.ts`, requirement.md's persona table), so this is a role-boundary breach, broader than the architects' A15 (waiter-vs-waiter scoping). Candidate `sev:critical` under the "a role reaches something it must not" clause if that definition is adopted, else `sev:major` | `controller/OrderPointAssignmentController.java:30`, `:91-95`; `service/OrderPointAssignmentService.java:155-157`; `service/PaymentService.java:134-147` |
| **F-9** | **Every tenant's customer page shows one tenant's legal identity.** `site-footer.component.ts:39` hard-codes `© RENDEZVOUS EVENTS S.R.L. — CUI 41973877`, and the footer is the NETOPIA/ANPC compliance block on the public customer page. So a customer at any other client's table is shown the wrong trader's name and tax id — a cross-tenant identity defect in the UI (the api is clean; nothing leaks server-side) and a consumer-protection problem, since the ANPC footer is exactly where the trader must be identified correctly. Raised by `ux` as a contradiction; verified here. Candidate `sev:major` — customer-facing, wrong legal entity, no workaround | `web/src/app/shared/site-footer.component.ts:39`; footer used on the customer surface per `web/CLAUDE.md` |
| **F-10** | **The footer's six legal links dead-end on the staff login form.** `/legal/:doc` is not routed, so it falls through `{ path: '**', redirectTo: 'login' }`; a customer tapping "Terms" or "ANPC" from the ordering page lands on the operator sign-in screen. Any mistyped or stale QR URL does the same. `web/CLAUDE.md` acknowledges the links are not ported, so this is a known hole rather than a surprise — but on a public surface it is customer-visible, and it interacts with F-9: the compliance footer both misidentifies the trader and cannot open its own documents | `web/src/app/app.routes.ts:230`; `shared/site-footer.component.ts` |

## 12. Risks

| Risk | Handling |
|---|---|
| The customer's browser leg through the real gateway is not executable locally | the money-critical half (the IPN that creates the order and the payment) is covered by R-O with a direct notify POST, plus L1/L2; the gateway's own behaviour stays a production-first risk and is called out at sign-off |
| The bridge is stubbed, not real hardware; the register's own behaviour (paper out, USB detached mid-print) is not reproduced | the stub covers the wire contract; hardware behaviour is a human gate (CLAUDE.md §9) |
| No CI (G-7): a merged fix can break another story's unit tests without anyone noticing until I run the suite | `qa` runs `mvn test` and `npm test` against `develop` before each execution round and reports a red suite as a blocker |
| The single-instance assumption (I8) cannot be falsified locally with one api container | reviewed by reading, not by test; any story touching the WS registries is escalated to the architects |
| Fixtures are hand-built until `devops` ships the seed scripts (F-4) | the curl script is kept with the test report so a run is reproducible |
