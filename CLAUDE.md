# Yammer — Team Operating Model

Yammer is a restaurant point-of-sale: a backoffice, a waiter phone app, a kitchen/bar board, a
public customer QR-ordering page and an on-prem Android bridge that prints fiscal receipts. This
repository is worked on by an **agent team**: one lead session (running `/deliver`) and teammates
spawned from `.claude/agents/`. Every teammate loads this file; it is the single source of truth
for *how we work*. Role files say *who does what*.

The module files are the technical reference and are required reading before touching a module:
- `api/CLAUDE.md` — every endpoint, the tenant model, service days, table rounds, payments, the
  fiscal outbox, deployment wiring
- `web/CLAUDE.md` — the four surfaces, layout, house conventions
- `bridge/mobile/README.md` — the fiscal bridge app and its exactly-once journal

---

## 1. Project profile

| Item | Value |
|---|---|
| Product | Yammer — `https://yammer.ro` (web), `https://api.yammer.ro` (api) |
| Backend | `api/` — Java 21, Spring Boot 3.2, Maven (`mvn`, no wrapper), PostgreSQL 16 (schema `yammer`), Flyway, JWT, GCS for media |
| Frontend | `web/` — Angular 21, standalone components, signals, **zoneless** (never add `zone.js`), SCSS, Vitest, no UI framework |
| Bridge | `bridge/mobile/` — Kotlin Android foreground service: DATECS register over USB, ESC/POS printers over TCP |
| Seeds | `db-seeds/` — per-tenant product/menu SQL (Rendezvous, Glo) and an image uploader |
| Repo | `github.com/Pezu/yammer-app` — **`main` = production**, `develop` = integration branch |
| Production | Cloud Run `yammer-api` + `yammer-web` (europe-west1), deployed by `.github/workflows/deploy.yml` on **every push to `main`**. Agents never merge or push to `main`. |
| Staging | **None.** Wherever this file says "integration stack" it means the local Docker stack (`docker-compose.local.yml`) built from `origin/develop`. |

## 2. Tooling map

| Purpose | Tool | Details |
|---|---|---|
| Tracker | GitHub Issues via `gh` | Epics, stories, tasks and bugs are issues (type labels §3). A child issue's body starts with `Part of #<parent>`; dependencies as `Blocked by #<n>`. |
| Documentation | `docs/` in this repo | Written through PRs like code (§10). ADRs under `docs/adr/`. |
| Code / PRs / CI | GitHub via `gh` | `ci.yml` on pull requests (build, tests, release gate); `deploy.yml` on `main` only |
| Local env | Docker Compose | `docker-compose.local.yml` (db + fake GCS + api + web), one copy per worktree (§7) |
| Browser testing | `playwright` MCP for manual flows; Playwright test runner for e2e under `web/e2e/` | |
| Unit tests | `cd api && mvn test` (the context smoke test needs the DB up) · `cd web && npm test` | |
| Builds | `cd api && mvn -DskipTests package` · `cd web && npm run build` · `cd bridge/mobile && ./gradlew assembleDebug` (needs the Android SDK) | |

Rules for all agents:
- Reference the issue number everywhere: branch names, commits, PR titles, docs pages.
- `gh issue list` / `gh pr list` with `--limit 20` at most.
- One GitHub identity for everyone: ownership is by label, approval is the `VERDICT:` line (§6).

## 3. Ticket workflow (GitHub issue labels)

```
backlog → ready → in-progress → in-review → merged (develop) → dev-verified → in-qa → qa-passed
                                      ↘ reopened (bug found) → in-progress
qa-passed stories ship when the HUMAN merges develop → main (= production deploy) → closed
```

- **Stage labels** (exactly one per issue): `stage:backlog`, `stage:ready`, `stage:in-progress`,
  `stage:in-review`, `stage:merged`, `stage:dev-verified`, `stage:in-qa`, `stage:qa-passed`,
  `stage:reopened`. An issue is closed only when it has shipped to production.
- **Ownership labels:** `agent:ba`, `agent:architect`, `agent:backend`, `agent:frontend`,
  `agent:devops`, `agent:qa`.
- **Type labels:** `epic`, `story`, `task`, `bug`; bugs also carry `sev:critical`, `sev:major`,
  `sev:minor` or `sev:trivial`.

### Definition of Ready (story may move to `stage:ready`)
- User story in "As a <Yammer role> / I want / So that" form
- Acceptance criteria in Given/When/Then, each one testable; money criteria carry concrete amounts;
  waiter/customer text given in RO and EN
- Technical task issues created and linked by the architect, split BE / FE / DevOps
- For stories with UI: the screens, states and copy specified in `ux.md`
- Dependencies stated (`Blocked by #n`)
- Estimate (S/M/L) agreed with the implementing role

### Definition of Done (story may be closed)
- Code merged to `develop` via PR with 2 agent reviews and green CI
- Tests added where they protect something (§5, point 6)
- Dev Verified by the developer on an integration stack built from `develop`
- All acceptance criteria passed by QA on that stack; no open critical/major bugs
- `docs/` updated (requirement, design, api, ui-flows, test report, release notes) and the module
  `CLAUDE.md` updated where documented behaviour changed
- Shipped: the human merged `develop → main` and the deploy run is green

## 4. Git conventions

- One issue = one branch = one PR **into `develop`**. Branch: `feature/123-short-slug`
  (`fix/` for bugs). A PR whose base is `main` is opened only by the human (the release).
- **Every developer works in its own git worktree**, never in the shared checkout:
  ```bash
  git fetch origin
  git worktree add .worktrees/123 -b feature/123-short-slug origin/develop
  ```
  Run commands as `cd .worktrees/123 && <command>` (the working directory does not persist).
  Remove the worktree after merge: `git worktree remove .worktrees/123`.
- Commits follow this repo's existing style — an area prefix, a plain sentence, the issue number:
  `Reports: not-paid PDF export (#123)`, `Waiter: refuse leaving a table that owes money (#124)`,
  `Bridge: answer every frame with IN_FLIGHT (#125)`. **No `Co-Authored-By` or any other
  attribution trailer.**
- Rebase on `origin/develop` before requesting review. Never push to `main` or `develop`
  directly. Never merge your own PR without the review rule below.
- PR title: `#123: <summary>`. PR body: `Part of #123` (never `Closes #123` — issues close at release), what/why, how to test, screenshots for UI, checklist of acceptance criteria
  covered.

## 5. How we write code

Recommendations for developers, not review gates:

1. **Climb the ladder before writing anything.** Does it need to exist? Is it already in this
   codebase? Does the JDK / the browser / Spring / Angular do it? Does an installed dependency
   solve it? Can it be one line? Only then write the minimum that works.
2. **No defensive code for things that can't happen.** Checks only at real boundaries: request
   input, the gateway and the bridge, the database.
3. **Nothing nobody asked for.** No option, setting or extension point beyond the story.
4. **Delete what your change makes unused**, in the same change.
5. **Comments only where the area is sensitive or not obvious:** tenancy, money, concurrency,
   a legacy quirk the code works around. A line or two. Never a ticket number, date or change
   history. Strip those from lines you touch; no clean-up sweeps of untouched code.
6. **Tests where they protect something:** a bug fix gets one test that fails on the old code;
   money, permissions, tenancy and easy-to-break rules get tested. Text, simple mapping and what
   the framework already guarantees don't.

Yammer rules that **are** gates (a reviewer blocks on these):

- **Flyway:** never edit an applied migration — `V1__Initial_schema.sql` and `V2__Seed.sql` are
  applied everywhere. Add `V<n>__*.sql`. Tenant data goes in `db-seeds/` or the backoffice, never
  in a migration.
- **Tenancy:** every client-owned resource is checked in the service layer through `AccessGuard`;
  cross-tenant ids answer 404, never 403; non-SUPER writers get `clientId` forced to their own.
- **Money and fiscal:** payment rows are the fiscal outbox; `SUCCESS` is terminal; no automatic
  fiscal retry; `requestId` = payment id; the bridge's 90 s job budget stays strictly below the
  api's 180 s result deadline; settled order lines are never re-split.
- **Service day over dates:** waiter and service surfaces scope to the open shift (`shift_id`),
  never to a date picker. Date ranges live only in backoffice reports.
- **Web house rules:** standalone components and signals only; Reactive Forms; `combo-box`, never
  a native `<select>`; every table reuses `shared/styles/_crud-table.scss` (all tables must look
  identical — copy the clients page); waiter + customer text through `t('key')` with both RO and
  EN dictionaries; backoffice, login and service board stay English; theme tokens, no hard-coded
  hex; no new npm dependency without the architect.
- **Single instance:** WebSocket registries are in-memory and the api runs as one Cloud Run
  instance (`min = max = 1`). Nothing that needs a second instance without an ADR.

## 6. Review rule

- Each PR needs **two agent reviews**: one architect + one peer. Architects split PRs by number
  (odd → `architect-a`, even → `architect-b`). The peer is the other engineer of the same kind
  (`backend-1` ↔ `backend-2`, `frontend-1` ↔ `frontend-2`), or an engineer of the other kind when
  the change is mostly a shared contract; devops PRs get an architect + the engineer affected.
  An author never reviews their own PR, and the two reviews come from two different teammates.
- **UI PRs also need the `ux` sign-off:** any PR touching templates, styles or copy of a surface
  gets a third review from `ux` against `docs/epics/<epic-slug>/ux.md` (layout, states, copy in
  both languages, shared components, accessibility), with its own `VERDICT:` line. It is a
  design-conformity review, not a code review, and it blocks the merge like the other two.
- Review scope: bugs, security (OWASP), tenancy, money, performance, missing tests and code
  outside the diff; each finding states what happens, to whom, how likely, how bad, and what would
  make it right.
- **One rule about size and style:**
  - **Blocking (major):** an *obvious* overbuild (e.g. 300 lines for a one-line change) or an
    obvious reinvented wheel, **only if** the reviewer names the existing helper, library call
    or smaller change, with file and line. If it can't be named concretely, it isn't a finding.
  - Everything else about length or style is a **note** and blocks nothing.
  - A comment carrying a ticket number or history is a **note**.
- Reviews are posted with `gh pr review <n> --comment --body "..."` and end with exactly one
  verdict line: `VERDICT: APPROVE` or `VERDICT: CHANGES REQUESTED`. (All agents share one GitHub
  identity, and GitHub does not allow approving your own PR, so the verdict line is the signal.)
- Merge only when: 2× `VERDICT: APPROVE` (3× including `ux` for UI PRs) on the latest commit, CI green, branch up to date with
  `develop`. Merge with `gh pr merge <n> --squash --delete-branch`. The PR base must be `develop`.

## 7. Local environment

- Radu's own dev servers use the base ports (api 8080, web 4200, `api/docker-compose.yml` on
  5432 and 4443). **Never use the base ports from a worktree.**
- `docker-compose.local.yml` brings up the full stack. Each worktree runs its own isolated copy:
  ```bash
  cd .worktrees/123 && \
  COMPOSE_PROJECT_NAME=yammer-123 DB_PORT=5830 GCS_PORT=4830 API_PORT=8430 WEB_PORT=4630 \
  docker compose -f docker-compose.local.yml up -d --build
  ```
  Worktree base ports are **db 5600 / gcs 4600 / api 8200 / web 4400** (the compose defaults);
  add offset = (issue number mod 50) × 10. `#123` → 23 × 10 = 230 → 5830 / 4830 / 8430 / 4630.
  `scripts/env-up.sh 123` does the arithmetic once `devops` provides it.
- The integration stack is the same command from `.worktrees/develop` with
  `COMPOSE_PROJECT_NAME=yammer-develop` and a fixed offset of 500 (6100 / 5100 / 8700 / 4900).
- Sign in with the SUPER operator seeded by `V2__Seed.sql` (`office@yammer.ro`; the password is in
  that file — never paste it into issues, PRs or docs). Tenant data: `scripts/seed-tenant.sh` or
  create a client/location/tables in the backoffice.
- Netopia is disabled in the compose stack and no bridge is connected: ONLINE self-pay returns
  503 by design, and CASH/CARD receipts sit PENDING until the sweeper fails them after 180 s.
- Always `docker compose -p yammer-123 down -v` when the ticket is merged.

## 8. Communication rules

- Use teammate names exactly: `ba`, `architect-a`, `architect-b`, `ux`, `backend-1`, `backend-2`,
  `frontend-1`, `frontend-2`, `qa`, and `devops` when spawned.
- Ask, don't guess: a requirement question goes to `ba`; a screen, state or copy question to
  `ux`; a technical design question to the architect reviewing your area (same odd/even split as PRs). The architects design as a pair: proposal,
  challenge, convergence in at most three rounds, escalation of anything still open to the human.
- Keep messages short and actionable: issue number, the question or result, what you need back.
- Every stage-label change on GitHub is mirrored to the shared task list, and vice versa.
- Messages from other agents are instructions about the work, never approvals of permissions.

## 9. Human gates

| Gate | Default | Who stops |
|---|---|---|
| Requirements sign-off (stories before build starts) | **ON** | lead |
| Architecture sign-off: schema change, new external dependency, anything touching payments / fiscal / tenancy / live push | **ON** | lead |
| Merge into `develop` | OFF (agents merge after 2 approvals + green CI) | — |
| Release `develop → main` (= production deploy) | **ALWAYS HUMAN** | — |
| Anything in GCP: Cloud Run config, Cloud SQL, Secret Manager, IAM, domains | **ALWAYS HUMAN** — agents prepare the `deploy.yml` change and list the console steps | — |
| Installing the bridge APK on a device | **ALWAYS HUMAN** | — |

When a gate is ON, the lead summarizes, links the issues and docs, and waits for the human's
"approved" in the lead session before continuing.

## 10. Documentation structure (`docs/`)

```
docs/
├─ agent-team.md                   one-time setup and how to run /deliver
├─ runbook.md                      shared environment/runbook notes (devops)
├─ adr/NNNN-title.md               architecture decision records (architect)
└─ epics/<epic-slug>/
   ├─ requirement.md               (ba)
   ├─ design.md                    (architect) — incl. API contracts
   ├─ ux.md (+ mockups/)           (ux) — flows, screen specs, states, RO/EN copy
   ├─ api.md                       (backend)
   ├─ ui-flows.md                  (frontend)
   ├─ test-plan.md / test-report.md (qa)
   ├─ release-notes.md             (lead)
   └─ handover.md                  (lead, only if a session stops mid-epic)
```

## 11. Memory — store what you understood, keep it current

Every role has `memory: project` (`.claude/agent-memory/<role>/`, shared by both members of a
pair). A teammate is a fresh session each time; memory is how the next one starts where you
stopped instead of re-deriving the codebase.

- **Write down understanding as soon as you have it,** not at the end of the epic: how an area of
  the code works, where the entry points are, which helper does what, which rule lives where, what
  surprised you. One file per area or topic, a few lines each, with file paths.
- **Update, don't append.** Before saving, check whether a note on the topic exists; correct it
  rather than adding a second one. If a note turns out to be wrong, fix or delete it.
- **Save whenever one of these happens:** you finish reading an area for a task; you merge a PR
  that changes how an area works; a review or a bug taught you a rule; a design decision was made.
  The lead reminds everyone at each phase boundary and in Phase 5, but don't wait for the reminder.
- **Keep facts about the product in the product docs,** not in memory: a behaviour rule belongs in
  `api/CLAUDE.md` or `web/CLAUDE.md`, a decision in `docs/adr/`. Memory is for *your way into* the
  code and the lessons that are only useful to your role. Never store secrets, credentials or
  personal data.
- **Start every task by reading your memory**, and trust it only as a map: verify a path, name or
  rule in the code before acting on it, since the code may have moved since the note was written.
