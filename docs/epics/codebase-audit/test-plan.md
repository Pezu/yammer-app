# Codebase audit — test plan

Epic: [#1](https://github.com/Pezu/yammer-app/issues/1) · Owner: `qa` · Phase 1 deliverable

Companion documents: `requirement.md` (`ba`), `design.md` (architects), `ux.md` (`ux`),
`test-report.md` (results, written in Phase 4).

---

## 1. What this plan is for

This is an **audit-then-fix epic**: the change set is not one feature but many small, scattered
edits to code that is already live in production (`yammer.ro`, deployed on every push to `main`).
That inverts the usual risk profile:

- the *individual* fixes are small and mostly obvious;
- the *aggregate* risk is high, because the fixes land across tenancy, money, the fiscal outbox,
  service days and four UI surfaces, and today almost none of those areas is protected by a test
  (§7 — 26 api tests, 1 web test, no e2e, `mvn test` red without a database).

So the testing strategy for this epic is **regression-first**: before fixes land, build a standing
regression spine over the invariants (§4); verify each fix against its own acceptance criteria
(§6) and re-run the spine suites its blast radius touches (§5.3). Per-story scenarios slot into
§9 as `ba` publishes stories — the structure is fixed now so nothing has to be reorganised later.

## 2. Scope

| In scope | Out of scope |
|---|---|
| Every accepted finding's fix, on the integration stack built from `develop` | Production (`yammer.ro`) — never a test environment |
| Regression of the invariant areas in §4, whether or not a fix touched them | Netopia end-to-end against the sandbox (no public tunnel in the stack; the IPN is tested by calling `/public/payments/netopia/notify` directly) |
| All four surfaces: backoffice (desktop), waiter (phone), service board (desktop/tablet), customer QR page (phone) | A real DATECS register / ESC-POS printer (no bridge connects to the stack; the fiscal path is tested to PENDING → sweeper → FAILED, and by driving `RECEIPT_RESULT` over the bridge WebSocket) |
| All five seeded roles — SUPER, ADMIN, WAITER, SERVICE, WATCHER — plus the unauthenticated customer | Installing the bridge APK on a device (human gate, CLAUDE.md §9) |
| Tenant isolation between two clients | Load/performance testing beyond the "large data" scenarios in §4 |
| RO and EN copy on the waiter and customer surfaces | Backoffice/login/service-board translation (English by design) |

## 3. Test levels and the automated/manual split

CLAUDE.md §5 point 6 is the rule: tests go where they protect something — money, permissions,
tenancy, easy-to-break rules, and every bug fix. Text and simple display stay manual.

| Level | Tool | Used for | Who |
|---|---|---|---|
| Unit (api) | JUnit 5 + Mockito, `cd api && mvn test` | Money arithmetic, allocation/split rules, service-day rules, fiscal state transitions, tenant decisions | backend (with the fix), `qa` reviews it exists |
| Unit (web) | Vitest, `cd web && npm test` | Pure logic in services/components: totals, state derivation, i18n key completeness | frontend |
| API integration | `curl` against the integration stack, scripted per scenario | Status codes, error reasons, auth, tenancy 404s, end-to-end payment/fiscal state, concurrency (two parallel requests) | `qa` |
| UI exploratory | `playwright` MCP — phone viewport for waiter/customer, desktop for backoffice | Screen states against `ux.md`, console/network errors, keyboard navigation, live push without reload, RO/EN copy | `qa` |
| UI e2e (automated) | Playwright test runner under `web/e2e/` — **does not exist yet, see QA-04** | A thin, stable set guarding money, permissions, tenancy and the flows that break most often | `qa` (through the normal PR flow) |

**What gets automated.** A scenario earns an automated test if it protects money, a permission, a
tenant boundary, a fiscal state transition, or a rule that has broken before — and if it can be
expressed without a real register or gateway. Everything else (copy, layout, iconography, drag
behaviour, print preview) is verified manually and recorded with a screenshot. Every
Critical/Major bug this epic finds gets a regression test with its fix, at the cheapest level that
would have caught it (prefer an api unit test over an e2e test).

**Where a test belongs.** Money/rule logic → api unit test. Role and tenancy decisions →
api integration scenario, plus an api test when the rule lives in a service. Cross-surface flow
(waiter pays → customer bill updates → service board refreshes) → e2e.

## 4. The regression spine

Seven suites over the invariants the epic must not break. They are the standing regression set:
every story re-runs the suites its blast radius touches (§5.3), and the whole spine runs once
before the epic is handed to the human for release.

### R1 — Money arithmetic (payments, splits, discounts, tips)

Fixture: location L1, table `T1.1`, products **Beer 12.50**, **Pizza 43.00**, **Water 7.00**,
payment types CASH/CARD/PROTOCOL/PO accepted on the point.
Two separate orders so allocation order is deterministic (allocation is oldest order first, then
line id as tie-break — within one order the order of two lines is not deterministic):
**Order A** = 3 × Beer (37.50), **Order B** = 1 × Pizza (43.00). Bill = **80.50**.

| # | Scenario | Expected |
|---|---|---|
| R1.1 | FULL, CASH, tip 5.00, no discount | payment `amount` 80.50, `tip` 5.00, `discount_amount` 0.00, `status` SUCCESS, `fiscal_status` PENDING; bill → unpaid 0.00, paid 4 units |
| R1.2 | PARTIAL, 2 × Beer | paid line 2 × 12.50 = 25.00; Beer line reduced to qty 1; payment `amount` 25.00; unpaid 55.50 |
| R1.3 | PARTIAL for a quantity larger than the unpaid one | 400, nothing settled, bill unchanged |
| R1.4 | AMOUNT 30.00 on the 80.50 bill | 2 Beers paid (25.00) + **one unit split**: paid part 5.00 and unpaid remainder 7.50, both carrying `originalPrice` 12.50; original line deleted (qty fully redistributed); paid 30.00, unpaid 50.50, total still 80.50 |
| R1.5 | AMOUNT 7.50 settling the remainder of a split unit | remainder paid; the bill **re-joins** the four fragments into 3 × Beer @ 12.50 with no partial marker (`mergeSettledSplits`); `originalPrice` on the stored rows is unchanged (display-only merge) |
| R1.6 | AMOUNT 100.00 on a 50.50 bill (overpay) | payment `amount` = 50.50, not 100.00; bill fully settled; **check the waiter UI does not claim 100.00 was taken** |
| R1.7 | AMOUNT 0.00 / negative / null | 400 "Amount must be positive"; nothing settled |
| R1.8 | Discount 10% on the point, unit price 13.33, 3 units, CASH FULL | per-unit rounding: 12.00 × 3 = 36.00 charged, `discount_percent` 10, `discount_amount` 3.99 (gross 39.99) |
| R1.9 | Same discounted table, PROTOCOL | never discounted: `amount` = gross 39.99, `discount_percent` null |
| R1.10 | Same discounted table, AMOUNT mode | by design the sum is taken as is, `discount_percent` null — **confirm with the architects that this is intended**, it means a discounted table charges undiscounted money in AMOUNT mode (triage question) |
| R1.11 | Pay with a payment type **not** configured on the order point | 400 "Payment type not accepted at this order point" |
| R1.12 | Pay when nothing is unpaid | 400 "Nothing to pay" |
| R1.13 | Settled lines are never re-split | after R1.2, a PARTIAL/AMOUNT settlement never alters a line with a `payment_id`; paid rows byte-identical before/after |
| R1.14 | Two devices pay the same table at the same time (two parallel FULL requests) | exactly one succeeds in settling each line; no line settled twice, no negative remainder, sum of payments = 80.50 |
| R1.15 | Double-submit of one payment (same request twice, fast) | the second gets "Nothing to pay" or settles only what is left — never a duplicate payment row for the same lines |
| R1.16 | APPROVAL/CANCELED/DRAFT orders | absent from the bill, from allocation and from the settle check |

### R2 — Fiscal outbox states

No bridge connects to the integration stack, so the PENDING → sweeper → FAILED path is the
natural one; SUCCESS/UNKNOWN are driven by connecting a fake bridge WebSocket client to
`/ws/bridge` (header `X-Bridge-Key`, HELLO, then `RECEIPT_RESULT`).

| # | Scenario | Expected |
|---|---|---|
| R2.1 | CASH payment, no bridge | `fiscal_status` PENDING at once; after `bridge.fiscal-result-timeout-seconds` (180 s) the sweeper sets FAILED; Payments report shows FAILED + Retry |
| R2.2 | CARD payment | same as R2.1 (the register records the mode) |
| R2.3 | PROTOCOL / PO payment | `fiscal_status` NONE, no bridge job, never appears in the waiter Payments list |
| R2.4 | `RECEIPT_RESULT` OK | SUCCESS + receipt number; **SUCCESS is terminal**: a later ERROR/UNKNOWN for the same `requestId` does not change it |
| R2.5 | `RECEIPT_RESULT` UNKNOWN | UNKNOWN; `retry-fiscal` → 409; `resolve-unknown {printed:true, receiptNumber}` → SUCCESS |
| R2.6 | `resolve-unknown {printed:false}` | `fiscal_reprint_authorized` set; the next RECEIPT carries `clearIntent` |
| R2.7 | `retry-fiscal` on FAILED | one new PENDING attempt, one job sent, `requestId` still the payment id |
| R2.8 | `retry-fiscal` on PENDING / SUCCESS / NONE | 409, no job sent (no automatic retry, ever) |
| R2.9 | A receipt is sent **once** | one job per payment; the accepting device is pinned in `fiscal_device`; a reconnecting bridge does not receive it again |
| R2.10 | Waiter `retry-fiscal` on their own FAILED payment vs another table's | own → OK; another waiter's table → 404; `resolve-unknown` as WAITER → 403/404 (ADMIN-only) |
| R2.11 | Budget invariant | the bridge's 90 s job budget stays strictly below the api's 180 s result deadline — assert the configured values, in a unit test, so a config edit cannot break it silently |

### R3 — Tenant isolation

Fixture: **client A** (location L1, tables, menu, users ADMIN-A/WAITER-A) and **client Z**
(location L2, ADMIN-Z/WAITER-Z) — two clients are mandatory seed data (§5.1).

| # | Scenario | Expected |
|---|---|---|
| R3.1 | ADMIN-Z reads every client-A id: location, order point, product, recipe, menu, menu tree, order, payment, shift, integration, user, qr-template | **404 for each, never 403 and never data** |
| R3.2 | ADMIN-Z writes to client-A ids (PUT/DELETE/PATCH/pay/assign/approve/close) | 404, and client A's row unchanged afterwards |
| R3.3 | ADMIN-Z creates a row with `clientId` = A in the body | `clientId` forced to Z (never A) |
| R3.4 | ADMIN-Z lists collections | only Z's rows; counts and report totals exclude A entirely |
| R3.5 | SUPER | sees and edits both clients; `clientId` not forced |
| R3.6 | WAITER-Z against client-A tables/orders/payments | 404 |
| R3.7 | Public customer endpoints (`/public/**`) | serve the point/menu/bill of the scanned table only; a token issued at a client-A table gives nothing at a client-Z table |
| R3.8 | Users list | never includes SUPER accounts; an ADMIN cannot grant SUPER (400/404) nor edit a SUPER target (404) |

### R4 — Roles and permissions

Covers the 56 `@PreAuthorize` rules in `api/src/main/java/com/yammer/controller/` (today exercised
by zero tests — QA-05) plus the client-side route guards.

| # | Scenario | Expected |
|---|---|---|
| R4.1 | For each role (SUPER, ADMIN, WAITER, SERVICE, WATCHER) × each endpoint family: allowed, forbidden, unauthenticated | matrix recorded in `test-report.md`; forbidden → 403, unauthenticated → 401, cross-tenant → 404 |
| R4.2 | SUPER-only catalogs (roles, order-point types, payment types, self-pay types, VAT, qr-templates, clients) | list authenticated, writes SUPER-only |
| R4.3 | Password login per role | any role with at least one role may log in; a user with no role gets the same 401 as bad credentials. **`api/CLAUDE.md` still claims login is restricted to ADMIN/SUPER via `AuthService.ALLOWED_LOGIN_ROLES`, which no longer exists (QA-07)** — tested against the code, and the doc gap is reported |
| R4.4 | QR login | `/auth/login/qr` exchanges the token for a session for any role; `POST /users/{id}/qr/reset` invalidates every previously issued QR (old token → 401) |
| R4.5 | Login throttling | 10 failures / 15 min per IP and per username → 429; a correct password after the lock still 429 until the window passes |
| R4.6 | Role home routing | ADMIN/SUPER → `/backoffice`, WAITER → `/waiter/tables`, SERVICE → `/service`, WATCHER → `/watcher`; `superGuard` keeps Clients/Roles out of an ADMIN's reach by URL, not only by menu |
| R4.7 | Expired/forged/missing JWT | 401 and a bounce to `/login`; no endpoint answers from a token with a tampered `clientId` or `roles` claim |

### R5 — Service day scoping

| # | Scenario | Expected |
|---|---|---|
| R5.1 | First order/payment with no open day | a day opens implicitly, attributed to whoever rang it up; reads (`/shifts/current`, reports) never open one |
| R5.2 | Order and payment stamping | every row carries the open `shift_id`; the waiter's Payments/Statistics and the dashboard match on that id. **`api/CLAUDE.md` states both "every order and payment is stamped with `shift_id`" and "orders and payments are NOT stamped with a shift id" (QA-08)** — the actual behaviour is what is tested and the doc gap is reported |
| R5.3 | Close with a table that owes | 409 naming every blocking table; the blocker list on the Shifts page matches and re-checks every 15 s |
| R5.4 | Close with only SERVICE stations assigned | allowed |
| R5.5 | Close and cash up | one row per waiter with card/cash/tips/protocol split; declined payments excluded; `closed_at` stamped; PROTOCOL shown on the slip but outside the waiter's total |
| R5.6 | After-midnight trade | an order placed at 01:30 against a day opened at 18:00 belongs to that day; the dashboard period combo selecting that day returns it; a date-range query over the calendar day does not split the day's money |
| R5.7 | Waiter surfaces have no date inputs | Orders/Payments/Statistics scope to the open day only — asserted in the UI |
| R5.8 | Second open day per location | refused (at most one open shift per location) |
| R5.9 | Settled money after a close | does not reappear in the next day's waiter lists |

### R6 — Table rounds, assignment and approvals

| # | Scenario | Expected |
|---|---|---|
| R6.1 | Assigning a waiter starts the round; the bill covers the round plus anything older still unpaid | money owed is never hidden by a re-assignment |
| R6.2 | Leaving / closing a table that owes | 409; a fully settled table can be left and closed |
| R6.3 | Two waiters on one table (multi-user point) | both see the same bill; both assignments shown; the round starts at the earliest assignment |
| R6.4 | Single-user point already taken | 409 naming the holder |
| R6.5 | Customer join | first scan → PENDING session; a token from the current round resumes APPROVED after a browser restart; a token from a previous round does not |
| R6.6 | CONFIRM mode | the customer order is a DRAFT: no number, absent from bill/kanban/waiter orders/reports, visible only on the assigned waiter's Approvals page; approve → numbered + ORDERED + pushed; deny → deleted |
| R6.7 | DISALLOW mode | menu browsable, ordering refused with the "ask a waiter" copy |
| R6.8 | Closing the table | drafts and customer sessions die with the round |
| R6.9 | Customer price tampering | the posted `menuItemId` price is ignored; the server resolves name and price from the menu |

### R7 — Live push and the service board

| # | Scenario | Expected |
|---|---|---|
| R7.1 | A waiter places an order | the board of the users routed to that point updates **without a reload** (WebSocket), within a couple of seconds |
| R7.2 | A kanban status change | pushed to the same set; the moving card appears in the new column on the other device |
| R7.3 | Push routing | only users whose board shows the order are notified: assigned station, else all SERVICE points of the home location |
| R7.4 | Socket dropped | the client reconnects (3 s) and polling covers the gap (8 s disconnected / ~64 s live); no order is missed across the gap |
| R7.5 | WebSocket handshake auth | a missing/invalid `?token=` is rejected; the bridge socket rejects a wrong `X-Bridge-Key` |

### Cross-cutting checks applied to every suite

- **Empty and large data:** a location with no tables/menu/orders renders empty states, not errors;
  a table with ~60 order lines and a location with ~40 tables stay usable, and the reports page
  with a few thousand rows paginates rather than hanging.
- **Boundaries:** quantity 0 and 1, amount 0.01 and exactly the bill, a 0.00-priced product,
  a 100% discount, the longest realistic table/product name.
- **RO and EN:** every waiter and customer screen read in both languages; no key shown raw, no
  English left on a RO screen, no layout break from the longer RO strings.
- **Console and network hygiene:** no console error and no failed request in any flow exercised.
- **Accessibility basics:** keyboard reach and visible focus on backoffice forms and tables; tap
  targets and contrast on the phone surfaces; `combo-box` operable by keyboard.

## 5. Environment

### 5.1 The integration stack

There is no staging. The integration stack is the local Docker stack built from `origin/develop`:

```
git worktree add .worktrees/develop origin/develop        # once
cd .worktrees/develop && \
COMPOSE_PROJECT_NAME=yammer-develop DB_PORT=6100 GCS_PORT=5100 API_PORT=8700 WEB_PORT=4900 \
docker compose -f docker-compose.local.yml up -d --build
```

Web on `http://localhost:4900`, api on `http://localhost:8700`. Never the base ports
(8080/4200/5432/4443) — those are Radu's own servers. A single-PR stack uses the same command from
that PR's worktree with its own offset (CLAUDE.md §7). Tear down with
`docker compose -p yammer-develop down -v` and rebuild from scratch whenever `develop` moves.

Stack properties that shape the tests:

- Netopia disabled → ONLINE self-pay returns 503 by design; the IPN is tested by posting to
  `/public/payments/netopia/notify` directly.
- No bridge connected → CASH/CARD receipts sit PENDING until the sweeper fails them after 180 s;
  SUCCESS/UNKNOWN paths need a fake bridge WebSocket client.
- fake-gcs replaces Google Cloud Storage for logos and menu images.
- One api instance — correct, and the only supported shape (in-memory WebSocket registries).

### 5.2 Seed data

`V2__Seed.sql` gives the global catalogs (roles WAITER/ADMIN/SERVICE/SUPER/WATCHER, payment types
CASH/CARD/PROTOCOL/PO, self-pay types, VAT 21/11/0, the QR template) and the SUPER operator
`office@yammer.ro` (password in that file — never copied into an issue, PR or doc).

On top of it, every test session needs **two clients** so tenancy is testable at all:

| | Client A | Client Z |
|---|---|---|
| Location | L1 "Rendezvous" (active) | L2 "Glo" (active) |
| Order points | T1.1…T6.1 (TABLE), B1 (BAR), S1 (SERVICE, kitchen) | T1.1…T3.1, S1 |
| Users | ADMIN-A, WAITER-A1, WAITER-A2, SERVICE-A, WATCHER-A | ADMIN-Z, WAITER-Z |
| Menu | from `db-seeds/rendezvous-*.sql` + the R1 fixture products at fixed prices | `db-seeds/glo-*.sql` |
| Point config | T1.1 accepts CASH/CARD/PROTOCOL/PO, routes to S1; T2.1 carries a 10% discount; B1 single-user; T3.1 self-pay ONLINE | defaults |

`CLAUDE.md` §7 points at `scripts/seed-tenant.sh` and `scripts/env-up.sh`; **neither exists**
(QA-03). Until `devops` provides them, the fixture is built through the backoffice UI and `curl`,
and the exact steps are recorded at the top of `test-report.md` so a run is reproducible.

### 5.3 Blast-radius map (which suites a change re-runs)

| A fix touches | Re-run |
|---|---|
| `OrderService`, `PaymentService`, `DiscountMath`, order item entities | R1, R2, R5, R6 |
| `BridgeService`, `FiscalResultSweeper`, `PaymentRepository` fiscal updates, bridge app | R2 |
| `AccessGuard`, `CurrentUserProvider`, any service's tenant check, `UserService` | R3, R4 |
| `SecurityConfig`, `JwtService`, `JwtAuthFilter`, `AuthService`, controller annotations, web guards | R4, R7.5 |
| `ShiftService`, reports, dashboard | R5, R1.1 |
| `TableRoundService`, `OrderPointAssignmentService`, `ApprovalService`, `CustomerAccessService` | R6, R1.13, R1.16 |
| `ws/`, `OrderNotificationService` | R7 |
| Any web template/style/copy on waiter or customer | the RO/EN and console checks of §4, plus `ux.md` conformity |
| `docker-compose.local.yml`, `Dockerfile`, `ci.yml`, nginx config | stack brings up clean from scratch + a smoke pass of one flow per surface |

## 6. How a fix is verified (Phase 4 loop)

1. The story arrives at `stage:dev-verified`. `qa` relabels it `stage:in-qa`.
2. Rebuild the integration stack from the current `develop` and record the commit SHA.
3. Walk the story's acceptance criteria one by one (§9 scenarios): api with `curl`, UI in a real
   browser at the right viewport, against the screen states in `ux.md`.
4. Run the spine suites the blast-radius map selects, plus the cross-cutting checks for the
   surfaces touched.
5. Add or extend the automated tests worth keeping (`web/e2e/`, api unit tests) through the normal
   PR flow.
6. Record every scenario in `test-report.md` with result, evidence and the `develop` SHA.
7. Bugs: a GitHub issue labelled `bug` + `sev:*` + the owning role label, body `Part of #<story>`,
   the engineer whose PR introduced it, environment and SHA, preconditions, exact steps,
   expected vs actual, evidence. Story → `stage:reopened`, message the developer with the number.
8. Sign-off: `stage:qa-passed` only when every acceptance criterion passed on the integration
   stack and no Critical/Major bug from that story is open. Minor bugs may remain if logged and
   accepted by the lead.

**Severity for this epic** (same as `requirement.md`): `sev:critical` — money wrong, data loss,
cross-tenant leak, fiscal double-print, a surface unusable; `sev:major` — a flow broken or a
business rule violated with a workaround; `sev:minor` — wrong but low impact; `sev:trivial` —
cosmetic.

## 7. Entry and exit criteria

**Entry to QA (per story)** — any one missing sends the story back without testing:

- `stage:dev-verified`, merged to `develop`, CI green (once `ci.yml` exists — QA-01/QA-02)
- Acceptance criteria present, in Given/When/Then, testable, money with concrete amounts, waiter/
  customer copy in RO and EN (§8 is the testability review)
- For UI stories, the screen states and copy specified in `ux.md`
- The integration stack builds and starts from the current `develop`

**Exit (per story):** every acceptance criterion passed; the selected spine suites green; no open
Critical/Major bug on the story; regression tests for each Critical/Major bug merged; results in
`test-report.md`.

**Exit (epic, before the human's release):** every accepted finding's story at `stage:qa-passed`;
the full spine run once against the release candidate on a stack rebuilt from scratch; `mvn test`
and `npm test` green **including** the context smoke test (QA-01); no open Critical/Major bug from
the epic; `test-report.md` complete with the SHA of the candidate; the §4 coverage gaps either
closed or recorded as accepted in `requirement.md`'s decisions log.

## 8. Testability review of acceptance criteria

`qa` reviews every story before `stage:ready` and replies to `ba` with concrete rewrites. A
criterion is rejected when it cannot be observed. The recurring fixes:

| Anti-pattern | Fix |
|---|---|
| "the total is correct" | give the amounts: "Given 3 × Beer @ 12.50 and 1 × Pizza @ 43.00, When CASH FULL with tip 5.00, Then the payment is 80.50 + 5.00 tip and the bill shows 0.00 unpaid" |
| "the page looks right" | name the state and the element: which empty/loading/error state, which label, in which language |
| "the user cannot do X" | name the actor and the answer: "an ADMIN of another client gets 404", "a WAITER gets 403" |
| "it is faster / cleaner / more robust" | state the observable: a response code, a count, a rendered value, an absent console error — or mark the story as non-functional and define the check |
| "the refactor changes no behaviour" | name the behaviours that must hold and which spine suites prove it; a pure-refactor story's criteria are "R-suites x, y pass unchanged" |
| Fiscal/async criteria with no timing | state the observable state and when: "`fiscal_status` is PENDING immediately and FAILED after the 180 s sweeper runs" |

## 9. Traceability — acceptance criterion → scenarios

Filled as `ba` publishes stories; one block per story, appended here, so `test-report.md` can
reference scenario ids directly.

| Story/Bug | Acceptance criterion | Scenarios | Level | Spine suites re-run | Result |
|---|---|---|---|---|---|
| _pending — stories not yet published_ | | | | | |

Scenario ids are `<story>-S<n>` for story-specific scenarios and `R<n>.<m>` for spine scenarios.

## 10. Coverage gaps found in Phase 1 (`QA-nn` findings)

Audited at `develop` = `628c193`. Evidence: `mvn -o test` in a clean worktree →
`Tests run: 26, Failures: 0, Errors: 1`; `npx ng test --watch=false` → `1 passed (1)`.

| ID | Gap | Evidence | Impact |
|---|---|---|---|
| QA-01 | `mvn test` is **red** without a Postgres on `localhost:5432`: `YammerApplicationTests` is a bare `@SpringBootTest` and there is no `api/src/test/resources` (no test profile, no Testcontainers, no in-memory datasource). On a clean machine or in CI the build fails with `Connection to localhost:5432 refused`. | `api/src/test/java/com/yammer/YammerApplicationTests.java:5`; surefire: `contextLoads » IllegalState Failed to load ApplicationContext`; `api/src/main/resources/application.yml:5` | High — blocks the epic's "green CI" DoD; owner `devops` + `backend` |
| QA-02 | No `.github/workflows/ci.yml` — nothing builds or tests a PR; only `deploy.yml` (on `main`) exists. | `.github/workflows/` holds `deploy.yml` only | High — DoD cannot be met; first `devops` task (D1) |
| QA-03 | The integration stack cannot be created from `develop`: `docker-compose.local.yml`, `web/nginx.local.conf`, `CLAUDE.md` and `docs/` are **untracked** — `git ls-tree origin/develop` lists only `.github .gitignore api bridge db-seeds web`. `scripts/env-up.sh` and `scripts/seed-tenant.sh` referenced by CLAUDE.md §7 do not exist either. | `git ls-tree --name-only origin/develop`; `git status --short`; no `scripts/` directory | High — Phase 4 has no environment until `devops` commits these |
| QA-04 | No e2e layer at all: `web/e2e/` does not exist, Playwright is not a dependency, `angular.json` has no e2e target — while CLAUDE.md §2 lists the Playwright test runner as team tooling. | `web/package.json` devDependencies; `angular.json` targets = build/serve/test | High — no cross-surface guard for money/permission flows |
| QA-05 | **The 56 `@PreAuthorize` rules and every `SecurityConfig` URL rule are untested.** There is not one web-layer test in the repo (no `@WebMvcTest`, no `MockMvc`, no `@SpringBootTest` with a request): every existing test is a plain Mockito unit test and `AccessGuard`/`CurrentUserProvider` are **mocked out**, so no test ever exercises a real authorization or tenancy decision. | `grep -c @PreAuthorize api/src/main/java/com/yammer/controller/*.java` = 56 across 23 controllers; `api/src/main/java/com/yammer/security/AccessGuard.java` has no test; `ShiftServiceTest` mocks `AccessGuard` | Critical — permissions and tenancy are exactly what CLAUDE.md §5.6 says must be tested |
| QA-06 | **The payment money path is untested.** `OrderService.pay` (FULL/PARTIAL/AMOUNT, ~200 lines of allocation, line splitting and rounding, `api/src/main/java/com/yammer/service/OrderService.java:446-700`) has no test; the only `OrderService` test covers the display-only `mergeSettledSplits`. `PaymentService` (168 lines), `BridgeService` (523 lines, the exactly-once fiscal send with `REQUIRES_NEW` steps and device pinning), `FiscalResultSweeper`, the guarded fiscal `@Modifying` updates in `PaymentRepository` (8 of them, SUCCESS-terminal), `OnlinePaymentService` (229 lines, the Netopia IPN), `UserService` (231 lines of tenant rules), `AuthService`, `JwtService`, `LoginAttemptService`, `CustomerAccessService`, `ApprovalService` and `OrderNotificationService` have **zero tests**. | `api/src/test/java` = 10 files / 1110 lines / 26 tests; none names Payment, Bridge, Auth, Jwt, User, Approval, Online | Critical — money and fiscal state are unprotected |
| QA-07 | `api/CLAUDE.md` documents that password login is restricted to ADMIN/SUPER "see `AuthService.ALLOWED_LOGIN_ROLES`"; that constant does not exist and `AuthService` lets any user with at least one role log in (`web/CLAUDE.md` says so correctly). QA would otherwise test the wrong rule. | `grep ALLOWED_LOGIN_ROLES api/src/main/java` → no match; `AuthService.java:38` rejects only an empty role set | Major — contradictory documented security rule; needs an architect decision on which is intended |
| QA-08 | `api/CLAUDE.md` contains both "**Every order and payment is stamped with `shift_id`** (V44) … every report matches on that id — never on a time window" and, in the same bullet, "Orders and payments are NOT stamped with a shift id: the window over `created_at` is the scope", plus `table_session` language after stating it was dropped in V46. The documented service-day rule is self-contradictory. | `api/CLAUDE.md`, the "Service day / shift" bullet | Major — the invariant QA tests against is ambiguous; architects to correct the doc |
| QA-09 | The web surfaces are effectively untested: 1 spec (`app.spec.ts`, "should create") for 42 components and 28 services — no test of the i18n dictionaries (RO/EN key parity is type-enforced but never asserted at runtime), of money formatting on the dashboard, of the auth interceptor/guards, or of the WebSocket reconnect logic. | `web/src/app/app.spec.ts`; `find web/src -name '*.spec.ts'` → 1 | Major — easy-to-break rules on four surfaces |
| QA-10 | `bridge/mobile/` has **no tests** (19 Kotlin files), including the exactly-once intent journal and the 90 s job budget that the fiscal invariant depends on; no test source set at all. | `find bridge -name '*Test*'` → nothing | Major — but constrained: the APK install is a human gate, so value is in pure-logic unit tests of the journal |

Minimum set `qa` asks for in Phase 2 (each as a story the engineers implement, `qa` reviews):
`ci.yml` + a context test that runs without a live database (QA-01/QA-02); the compose stack and
seed scripts committed (QA-03); api unit tests for `OrderService.pay` in all three modes with the
R1 amounts (QA-06); a web-layer test slice asserting role + cross-tenant 404 on one endpoint per
controller family (QA-05); `web/e2e/` bootstrapped with three Playwright specs — waiter pays a
table, an ADMIN of another client gets nothing, a service-board card moves on push (QA-04).

## 11. Risks

| Risk | Mitigation |
|---|---|
| Many small fixes across sensitive areas, no safety net today | the spine (§4) runs before the first fix lands and after each one; QA-01/02/04/05/06 prioritised so the net exists early |
| No staging; production is the only "real" environment | release candidate tested on a stack rebuilt from scratch from `develop`; release stays a human gate |
| Fiscal and gateway paths cannot be fully reproduced locally | fake bridge WebSocket client for `RECEIPT_RESULT`, direct IPN posts for Netopia; what cannot be covered is listed explicitly in `test-report.md` as untested-by-environment |
| 180 s sweeper makes fiscal scenarios slow | assert PENDING immediately, batch the FAILED checks at the end of a session, and cover the transition itself in a unit test with a shortened timeout |
| Refactor-shaped findings have no observable acceptance criteria | §8 forces them to name the spine suites that prove behaviour is unchanged |
| Doc contradictions (QA-07, QA-08) mean testing against the wrong rule | raised to the architects in Phase 1; no story touching those areas enters QA until the doc states one rule |
