# Yammer Web

Angular frontend for the Yammer project. This is a **from-scratch rebuild**
(started 2026-08-29); the previous full implementation lives at
`~/Projects/yammer-old/web` and serves as the reference. Screens are ported one
at a time — so far: **login** (verbatim), the **backoffice layout** (sidebar with
a Configuration group + top-bar user menu, trimmed to the ported pages), and the
**Clients**, **Users** and **Roles** pages (verbatim, incl. logo upload, the
SUPER-only client filter combo on Users, and role checkboxes fed by `GET /roles`).

**Lightweight by design** — no CSS framework, no UI library, no web fonts.
Screens are visually modelled on the **Duralux** admin template
(`~/Projects/yammer-old/duralux`, visual reference only — never vendor its CSS),
but markup and styles are hand-written.

## Stack

- **Angular 21** (standalone components, signals, zoneless — no `zone.js`, don't add it)
- **TypeScript**, **SCSS**; Reactive Forms; `HttpClient` (fetch backend)
- Global `src/styles.scss` = reset + CSS custom-property theme tokens
  (`--primary: #3454d1`, `--text`, `--muted`, `--border`, `--danger`, `--page-bg`).
  Reference the tokens in component styles, don't hard-code hex.

## Commands

Run from `web/`.

- Install: `npm install`
- Dev server: `npm start` → http://localhost:4200 (proxies `/api` → backend on 8080)
- Build: `npm run build` (output in `dist/web/`)
- Unit tests: `npm test`

## Layout

```
web/src/app/
├── app.{ts,html,config,routes}       # root component + router + providers
├── core/
│   ├── i18n.service.ts               # RO/EN runtime translations for the waiter + customer surfaces:
│   │                                 #   `lang` signal + `t(key, params)`; waiters default RO, customers
│   │                                 #   follow the browser (RO fallback); choice remembered per scope in
│   │                                 #   localStorage (yammer.lang.waiter / yammer.lang.customer)
│   ├── i18n/translations.ts          # EN reference dictionary + RO (typed: every EN key required)
│   ├── auth.service.ts               # login(), session signal, localStorage persistence
│   ├── auth.interceptor.ts           # Bearer token + bounce to /login on 401
│   └── auth.guard.ts                 # authGuard for routes that need a session
│   └── super.guard.ts                # superGuard for SUPER-only routes (e.g. clients)
├── features/
│   ├── auth/login/                   # the login screen (verbatim from yammer-old)
│   ├── auth/qr-login/                # /login/qr/:token — exchanges a scanned QR token for a session
│   ├── service/                      # /service — kitchen/bar kanban (SERVICE users land here via
│   │                                 #   ROLE_HOME). First a station picker (the location's SERVICE
│   │                                 #   points, Free/Yours/Taken, via /order-points/stations) unless
│   │                                 #   one is already mine; the top-bar station pill reopens it.
│   │                                 #   ROLE_HOME): Ordered/Ready columns, pointer drag + action
│   │                                 #   buttons, fullscreen + wake lock; live refresh over
│   │                                 #   /api/ws/orders (WebSocket, auto-reconnect 3 s) with polling
│   │                                 #   as the safety net (8 s while disconnected, ~64 s while live)
│   ├── customer/                     # /customer/order-point/:id — PUBLIC ordering page behind the
│   │                                 #   printed QR codes (ported from old yammer): client logo
│   │                                 #   top-left, hamburger drawer top-right (Menu first + legal
│   │                                 #   links), category quick-nav chips, menu list with images +
│   │                                 #   qty steppers, session-persisted cart, Place order. Drawer =
│   │                                 #   Menu · Language · Order (the table's bill: To pay + Paid
│   │                                 #   section when non-empty; split units tinted + half-circle icon).
│   │                                 #   Menu is
│   │                                 #   always browsable; ordering 409s with "ask a waiter" while
│   │                                 #   the table has no open session. (No Orders history / pay-now
│   │                                 #   flow yet — those come with the customer-identity port.)
│   │                                 # Approval flow: first scan auto-joins the open session (token in
│   │                                 #   localStorage so re-scans resume it), "waiting for waiter"
│   │                                 #   banner polls until APPROVED; DISALLOW mode = menu only
│   │                                 #   (with a "self-ordering is off" notice); the after-order
│   │                                 #   message uses the server's pendingApproval flag, not the
│   │                                 #   cached table mode, and the table state is re-read after
│   │                                 #   each order;
│   │                                 #   CONFIRM mode = orders are DRAFTs on the waiter's Approvals page first
│   │                                 #   (no number until approved; deleted if denied).
│   │                                 # ONLINE self-pay: Place order redirects to Netopia; the
│   │                                 #   payment-return page polls /public/payments/{ref}/status.
│   ├── waiter/approvals/             # hamburger → Approvals: pending customer joins + APPROVAL
│   │                                 #   orders on MY tables, Approve/Deny, 10s polling
│   ├── waiter/waiter-page.ts         # /waiter shell — topbar + hamburger menu (Logout last) + outlet
│   ├── waiter/tables/                # /waiter/tables — the waiter's ASSIGNED tiles + a "+" tile
│   │                                 #   opening the assign picker (full TABLE/BAR board with
│   │                                 #   status: multi-user points show "N assigned" and always
│   │                                 #   accept; single-user show Free/Yours/Taken — <name>).
│   │                                 #   The picker refreshes its tiles in place (no flash).
│   │                                 #   Assigning OPENS the table session (unassign is refused
│   │                                 #   with a 409 message while the session is open — only
│   │                                 #   Close table frees it). Tapping a tile → /waiter/tables/:id = the
│   │                                 #   session's combined bill; header = name · self-order combo
│   │                                 #   (Allowed/Confirm/Disabled); sticky footer = Pay (only while something is due) + Order, half width each; Unpaid/Paid view combo
│   │                                 #   (default Unpaid), the pay sheet, and — once everything
│   │                                 #   is paid — the "Close table" button, the ONLY way to end
│   │                                 #   the session (frees the table, back to the tiles).
│   │                                 #   → /waiter/tables/:id/order (menu drill-down +
│   │                                 #   search + cart, ported from old yammer minus payments;
│   │                                 #   menus cached in waiter-menu-cache.service via localStorage)
│   └── backoffice/
│       ├── backoffice-layout.*       # sidebar (Configuration + Menu group [Products/Recipes/Menu]
│       │                             #   + Reports group [empty for now] + SUPER-only Catalog) + topbar
│       └── pages/
│           ├── clients/              # clients CRUD + logo upload (verbatim)
│           ├── users/                # users CRUD + client filter combo + QR-login popup
│           ├── locations/            # locations CRUD + client combo (verbatim) + per-row QR
│           │                         #   export (downloads the customer-ordering QR PDF)
│           ├── products/             # per-location catalog (rich-HTML name/desc/VAT/image;
│           │                         #   NO price — that's per menu entry; newest first)
│           ├── recipes/              # product recipes — fractional composition of other products
│           ├── menu/                 # menu tree editor; leaves PICK catalog products (no inline
│           │                         #   price/VAT/image on product nodes — those live on Products)
│           ├── integrations/         # "Peripherals" — cash registers, printers and MOBILE rows (a bridge
│           │                         #   phone picked from the connected bridges, with an online dot);
│           │                         #   a register/printer is TCP (ip) or Mobile (attached to a MOBILE row)
│           ├── order-points/         # list + batch "add multiple" modal (type/count/self-pay/…)
│           ├── self-pay-types/       # catalog CRUD (SUPER-only; ONLINE/CHECK — how a customer
│           │                         #   self-pays at an order point; cloned from payment-types)
│           ├── reports/              # Orders report (paginated + per-column filter combos, right-hand
│           │                         #   editable detail: unpaid qty steppers, delete when unpaid;
│           │                         #   ported from old yammer minus events — filter-combo.ts is the
│           │                         #   small in-table dropdown it uses)
│           │                         # + Payments report (table/waiter/amount/tip/total/type + totals row
│           │                         #   + Fiscal column: receipt no. / PENDING / FAILED→Retry / UNKNOWN→
│           │                         #   Printed | Not printed, backed by /payments/{id}/retry-fiscal and
│           │                         #   /resolve-unknown)
│           │                         #   + Open tables report (open sessions: table/opened by/
│           │                         #   opened at/outstanding amount + totals row)
│           ├── order-point-types/    # catalog CRUD (SUPER-only; cloned from roles)
│           ├── payment-types/        # catalog CRUD (SUPER-only; cloned from order-point-types)
│           ├── roles/                # roles CRUD (verbatim; SUPER-only)
│           └── vat/                  # VAT catalog CRUD (verbatim; SUPER-only)
└── shared/
    ├── combo-box.ts                  # custom dropdown (duralux Select2 look) — NEVER use a native <select>;
    │                                 #   [multiple]+[values]/(valuesChange) = multi-select with ticks
    ├── rich-text-editor.ts           # compact HTML editor (size/bold/italic/sup/sub) — product names
    ├── transparent-image.directive.ts # hides imgs detected as fully transparent (menu items)
    ├── confirm-dialog/               # confirmation modal used by CRUD deletes
    ├── styles/_crud-table.scss       # shared table-panel styles — @use from every CRUD page
    ├── logo.component.ts             # inline-SVG Yammer wordmark
    └── site-footer.component.ts      # NETOPIA/ANPC/legal compliance footer
```

## Conventions

- Standalone components only; signals for component state; Reactive Forms for inputs.
- Feature code under `features/<feature>/`; shared singletons under `core/`.
- Semantic markup + scoped SCSS using the global theme tokens. No utility frameworks.
- `environment.apiUrl` is `/api`; dev proxy in `proxy.conf.json`.
- The footer's legal links route to `/legal/:doc`, which is not ported yet and
  currently redirects to `/login` via the wildcard route.
- Login lands users on a role-based home (`ROLE_HOME` in `login.ts`); ADMIN and
  SUPER go to `/backoffice` (default child: `users`), and the backend only lets
  ADMIN/SUPER log in. The Clients and Roles menu entries and routes are SUPER-only
  (`superGuard` + `@if (isSuper())` in the sidebar).
- **Waiter + customer UI text goes through `t('key')`** (inject `I18nService`, expose
  `readonly t = this.i18n.t`); add every new string to BOTH dictionaries in
  `core/i18n/translations.ts`. Combo options that need translating are `computed()`.
  The backoffice, login and service board stay English (no `t()` there).
- - **All tables must look identical** — reuse `shared/styles/_crud-table.scss`
  (the `.table` panel pattern); copy the clients page as the template for new
  CRUD pages.

## Deployment (GCP, Cloud Run)

Pushing to `main` runs `.github/workflows/deploy.yml`: `web/**` changes build
`web/Dockerfile` (npm ci + `ng build`, served by nginx on 8080 with `web/nginx.conf`)
and deploy Cloud Run service **`yammer-web`** (europe-west1, scale to zero). nginx
proxies `/api/` to the api service's run.app URL and strips the prefix (same as the
dev proxy), so the SPA never needs the custom api host. Custom domains (Cloud Run
domain mappings, DNS in Cloud DNS): `yammer.ro` (main), `rendezvous-app.ro`, `servioapp.ro`.
