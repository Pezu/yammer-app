# Yammer API

Spring Boot REST API for the Yammer project. This is a **from-scratch rebuild**
(started 2026-08-29); the previous full implementation lives at
`~/Projects/yammer-old/api` and serves as the reference. Features are ported
over one slice at a time — so far: **authentication/login**, **clients CRUD**
(incl. logo upload to GCS), **users CRUD** and the **roles list**.

## Stack

- **Java 21**, **Spring Boot 3.2.1** (via `spring-boot-starter-parent`), **Maven**
- **PostgreSQL** via Spring Data JPA (`ddl-auto: validate` — schema owned by Flyway)
- **Flyway 10.x** (`flyway-database-postgresql` is an explicit dependency)
- **JWT** via jjwt (HS256); bean validation; **Lombok**
- Group `com.servio`, artifact `yammer`, base package **`com.yammer`**

## Commands

Run from `api/`.

- Local DB + storage: `docker compose up -d` (Postgres on 5432, user/pass
  `yammer`/`yammerpass`, `init-schemas.sql` creates the `yammer` schema on first
  start; fake-gcs-server on 4443 emulates GCS for logo uploads)
- Build: `mvn -DskipTests package`
- Run locally: `mvn spring-boot:run -Dspring-boot.run.profiles=local` (port 8080).
  The `local` profile (`application-local.yml`) supplies the dev JWT secret and
  datasource; without it `JwtService` refuses the committed placeholder secret.
- Tests: `mvn test` (the context-load smoke test needs the DB up)

## Endpoints

- `GET /` → plain-text health string
- `GET /actuator/health`
- `POST /auth/login` → `{ username, password }` → `{ token, username, roles, clientId }`;
  401 on bad credentials, 429 after 10 failures/15 min per IP or username
  (`LoginAttemptService`). **Login is restricted to ADMIN and SUPER** — other
  roles get the same 401 as bad credentials (see `AuthService.ALLOWED_LOGIN_ROLES`).
- `/clients` — list (authenticated; SUPER → all, others → own client),
  create/update/delete + logo upload/delete (SUPER-only via `@PreAuthorize`);
  `GET /clients/{id}/logo` is public so `<img>` can load it. Logos live in GCS
  (`StorageService`, emulated locally by fake-gcs; see `StorageConfig`).
- `/users` — CRUD, `@PreAuthorize hasAnyRole('ADMIN','SUPER')` on the controller.
  The list never includes SUPER accounts (system accounts, managed outside the UI);
  `UserService` enforces the tenant model: SUPER sees/edits all, an ADMIN only
  their own client's users (cross-tenant/SUPER targets → 404), cannot grant
  SUPER, and rows they write are forced onto their own client. A create needs only
  a name or a username: a blank username falls back to a random UUID and a blank
  password to a generated 192-bit secret (QR-only sign-in). Blank username/password
  on update = keep current.
- `/roles` — list for any authenticated user, writes SUPER-only.
- `/order-point-types` — global catalog like roles (seeded SERVICE/BAR/TABLE);
  list for any authenticated user, writes SUPER-only.
- `/vat-types` — global VAT catalog (a VAT type is just its percentage value;
  seeded 21/11/0); list for any authenticated user, writes SUPER-only.
- `/payment-types` — global catalog like the others (seeded CARD/CASH/PROTOCOL/PO);
  list for any authenticated user, writes SUPER-only.
- `/self-pay-types` — global catalog like the others (seeded ONLINE/CHECK): how a
  CUSTOMER self-pays at an order point (`order_point.self_pay_type_id`, optional);
  replaces the removed pay_later boolean. List authenticated, writes SUPER-only.
- `/order-points` — ADMIN/SUPER; `GET ?locationId=` (tenant-checked via
  `AccessGuard.requireAccessibleLocation`), `POST /batch` creates N points at once
  auto-named by type (first letter + number: B1…, S1…; TABLE points are born as
  T{n}.1 — the split-slot scheme from the old app's M{n}.1 — numbering continues
  past the location's max), `PUT /{id}`, `DELETE /{id}`. Lists are naturally
  ordered: prefix groups alphabetically, numbers numerically (B2 < B10). A point's `service_order_point_id`
  must be in the same location (UI offers only SERVICE-type points);
  `menu_id`/`printer_id`/`cash_register_id` are plain UUIDs (their combos are
  wired to `/menu/menus` and `/integrations`; FK migrations still pending).
- **Table sessions** (V24): assigning a table OPENS a `table_session` for its order
  point (or joins the one already open — at most one open per point, enforced by a
  partial unique index). Orders and payments are stamped with the session; the
  bill and the waiter's paid/unpaid views cover ONLY the current open session.
  Unassigning is refused (409) whenever the point has an open session — an open
  table is left ONLY by closing it. The close point is `POST /order-points/{id}/close`
  (any authenticated user, tenant-checked): 409 while unpaid lines remain,
  otherwise closes the session (closed_by/closed_at) AND clears every assignment
  on the point, freeing the table.
- **Ordering** (`/orders`, any authenticated user, tenant-checked via AccessGuard):
  `POST` places an order (line names+prices snapshotted; `order_no` = per-client
  max+1; status always ORDERED until the delivery/payment flows are ported) —
  requires the point's OPEN session (400 otherwise) and stamps it on the order.
  `GET ?orderPointId=` lists the current session's orders newest-first.
  `GET /order-points/{id}/menu` bundles the point + its default menu tree + the
  location's menus (switcher) + all orderable products (search) for the waiter
  ordering screen. `GET /orders/bill?orderPointId=` = the current session's
  combined bill (lines aggregated per product+price, split paid/unpaid, + the
  point's accepted paymentTypeIds + `sessionOpen`; empty when no session is
  open). `POST /orders/pay {orderPointId, paymentTypeId, mode, tip,
  amount?, items?}` — the type MUST be one configured on the order point. Modes:
  FULL (everything unpaid), PARTIAL (selected product quantities; lines split as
  in the old PaymentSplitService — original stays unpaid reduced, new paid line
  inserted), AMOUNT (fixed sum allocated oldest-order-first; the last unit may be
  partially paid — its price splits into a paid line and an unpaid remainder
  line). `payment` rows link settled `order_item.payment_id` (V22).
- **Orders report** (ADMIN/SUPER, ported from old yammer minus events):
  `GET /orders/page?locationId=&page=&size=` + optional `orderNo`/`orderPointId`/
  `waiter` (raw created_by)/`paid` (NOT|PAR|PAID) — server-side paged rows with
  items, total and the derived paid state; `GET /orders/filter-options?locationId=`
  (points + waiters as username/display-name pairs);
  `PATCH /orders/{id}/items {items:[{id, quantity}]}` edits UNPAID item
  quantities (≤0 deletes the line; paid lines untouched);
  `DELETE /orders/{id}` only when nothing is paid. All in `OrderReportService`.
- `GET /payments?locationId=` — the payments report (ADMIN/SUPER): table, waiter
  (display name), amount, tip, total, payment type, newest first.
- **Kanban statuses**: orders move ORDERED → READY → DELIVERED (or CANCELED) via
  `PATCH /orders/{id}/status` (any authenticated user, tenant-checked). The
  service board (`GET /order-points/service-board`) shows ORDERED/READY orders
  from points routed to the caller's assigned SERVICE stations (+ the stations
  themselves); a caller with no assigned station falls back to ALL SERVICE
  points of their home location. APPROVAL/CANCELED orders never hit the bill,
  pay allocation, or the settle checks.
- **Customer approval** (`ApprovalService` / `CustomerAccessService`): a customer
  device scanning a table QR calls `POST /public/order-points/{opId}/join`
  ({token?}) — a valid token for the CURRENT open session resumes as-is
  (approval survives browser restarts; the token lives in the customer's
  localStorage), anything else creates a PENDING `customer_session` for the
  assigned waiter. `GET /public/order-points/{opId}?token=` reports
  `selfOrderMode` + `customerStatus` (+ `selfPayOnline`); customer order
  placement requires APPROVED + mode ≠ DISALLOW, and under CONFIRM the order is
  born in APPROVAL status. Waiter side: `GET /approvals` (pending joins +
  APPROVAL orders across MY assigned tables), `POST /approvals/customers/{id}`
  and `POST /approvals/orders/{id}` `{approve}` (deny deletes the order),
  `PUT /order-points/{id}/self-order-mode` (assigned users only).
  `GET /public/order-points/{opId}/bill?token=` = the session's aggregated bill (unpaid +
  paid lines, totals) for an APPROVED device (403 otherwise) — the customer page's Order view.
  A split unit carries `originalPrice` on BOTH halves (paid + remainder) so both render as
  partial; once every fragment of a unit is paid the bill re-joins them into one regular
  unit at the original price (`OrderService.mergeSettledSplits`, display-only, unit-tested).
  Closing the
  table invalidates every customer session with it (they hang off table_session).
- **Payment lifecycle by type** (`OrderService.createPayment`): CASH → SUCCESS +
  fiscal-print request to the bridge (LOGGED for now); CARD → PENDING until
  `POST /public/softpos/callback` `{paymentId, success}` marks SUCCESS or FAILED
  (failure releases the payment's order lines back to unpaid); PROTOCOL / PO →
  SUCCESS with no fiscal printer (as in old yammer). The payments report
  excludes FAILED rows.
- **Netopia online self-pay** (`OnlinePaymentService`, ported minus events): at
  an order point whose self_pay_type is ONLINE, a customer order parks the cart
  as an `online_payment` intent and returns the gateway `paymentUrl`; the order
  + ONLINE payment are created ONLY on the confirmed server-to-server IPN
  (`POST /public/payments/netopia/notify`); the return page polls
  `GET /public/payments/{ref}/status`. Config under `netopia.*` (sandbox keys in
  the local profile; `notify-url` needs a public tunnel). Stale PENDING intents
  expire after 30 min (`@EnableScheduling`). Billing identity is anonymous
  ("Guest Customer") — the old customer phone/email lookup was NOT ported.
- `GET /reports/open-tables?locationId=` — open-tables report (ADMIN/SUPER): the
  location's OPEN table sessions on TABLE-type points only (no bars), naturally
  ordered by table name, with who opened each, when, and the outstanding unpaid
  amount.
- `GET /bridge/devices` — stub returning `[]` until the on-prem bridge (WebSocket)
  subsystem is ported; the peripherals page polls it for the USB device picker.
- **Assignment (new model, replaces the old per-event one)**: `order_point_assignment`
  (point↔user, V17). Self-service under `/order-points` (any authenticated user):
  `GET /assigned` (my points), `GET /assignable?locationId=` (board with
  assignedCount / assignedToMe / assignedNames), `POST|DELETE /{id}/assign`
  (idempotent; a single-user point — allow_multiple_users=false — returns 409
  when already taken by someone else). Waiters see only their assigned points
  and pick more from the board.
- `/products` — the per-location **product catalog** (rich-HTML name, description,
  VAT, image; NO price — price is per MENU ENTRY): the single source of truth,
  listed newest-first. Writes ADMIN/SUPER, list authenticated. Deleting a product
  cascades to menu items + recipe rows referencing it.
- `/recipes` — a product's composition out of other products of the same location
  (fractional quantities, e.g. 0.0714 of a prosecco bottle): `GET /{productId}`,
  `PUT /{productId}` replaces the rows. For rolling orders up into raw product
  consumption later.
- Menu leaves REFERENCE catalog products (`menu_item.product_id`); a leaf's
  name/price/VAT/image are derived from the product at read time (name also
  snapshotted). Categories keep their own name + image.
- `/menu` — menus are **per location** (no event level, unlike the old project):
  `GET/POST /menu/menus?locationId=`, `DELETE /menu/menus/{id}`,
  `GET|PUT /menu/menus/{id}/tree` (id-preserving reconcile save),
  `POST /menu/image` (GCS upload). `GET /public/menu-image?object=` serves item
  images publicly (`/public/**` is permitAll).
- `/integrations` — printers + cash registers per location ("Peripherals" in the
  UI): CRUD, writes ADMIN/SUPER, list for any authenticated user with optional
  `?type=`. TCP (ip) or USB (bridge device_id) connections.
- `/locations` — CRUD, writes ADMIN/SUPER; list for any authenticated user with
  `?clientId=` filter. `active` (V29) is toggled inline from the Locations page; the
  backoffice pages auto-select a location only when exactly one ACTIVE one exists. `GET /locations/{id}/qr` (ADMIN/SUPER) = printable PDF of
  customer-ordering QR codes, one per TABLE/BAR point of the location, naturally
  ordered (ported from old yammer's per-EVENT `QrPdfService`; iText 7 + ZXing).
  Each QR encodes `<app.base-url>/customer/order-point/{opId}` — the public
  customer ordering page. `GET /public/order-points/{opId}` returns id + name +
  clientId (logo) + `sessionOpen` + the table's full menu tree
  (`MenuService.getTreeUnchecked` — no tenant check, it's public by design).
  `POST /public/order-points/{opId}/orders {items:[{menuItemId, quantity}]}` places
  a customer order — name/price resolved SERVER-side from the menu (no price
  tampering), stamped `createdBy="Customer"` into the point's OPEN session; 409
  "Table is not open" when there is no open session (menu stays browsable, only
  ordering is gated). Tenant-scoped via `security/AccessGuard`
  (`requireAccessibleLocation`), the shared home of the cross-tenant-404 rule —
  extend it as more client-owned resources are ported.
- **QR login**: `GET /users/{id}/qr` (ADMIN/SUPER, tenant-scoped) returns
  `{ url, png }` (`Cache-Control: no-store`) — the login URL
  `<app.base-url>/login/qr/<qr_token>` plus its QR as a base64 PNG (ZXing,
  `QrCodeService`). The token is a per-user random UUID generated on first
  request and stored in `users.qr_token`; `POST /users/{id}/qr/reset` replaces it
  with a fresh UUID, invalidating every previously issued QR for that user.
  `POST /auth/login/qr {token}` (public,
  IP-throttled) exchanges it for a session; unlike the password form it is NOT
  restricted to ADMIN/SUPER (the QR is a direct credential handed out by an
  operator). `app.base-url` is set via the `APP_URL` env var (default
  http://localhost:4200); the `local` profile overrides it with the dev machine's
  LAN IP so the QR is scannable from a phone.

## Authentication & authorization

Same model as the old project:

- Passwords stored as **BCrypt** (`PasswordHasher`); legacy MD5-hex values are
  accepted and transparently re-hashed to BCrypt on successful login.
- JWT carries `sub` (username), `roles`, `clientId` and `locationId` (both
  omitted for SUPER; locationId = the user's home location).
  `JwtAuthFilter` parses it into a `UserPrincipal` with `ROLE_<name>` authorities;
  no per-request DB lookup. Services access the caller via
  `CurrentUserProvider.require()`.
- Two-tier model: **SUPER** = everything, no tenant scope (`client_id = null`);
  everyone else acts within their own client. Method-level rules via
  `@PreAuthorize`; keep `SecurityConfig` URL rules minimal.
- When porting resources from the old project, follow the authorization checklist
  in `~/Projects/yammer-old/api/CLAUDE.md` (tenant-scope in the service layer,
  404 for cross-tenant ids, force `clientId` for non-SUPER writers).

## Database

- Flyway migrations in `src/main/resources/db/migration`; **never edit an existing
  migration** — add a new `V<n>__*.sql`.
- `V1__init.sql`: `role`, `client`, `users` (with `name`, `roles TEXT[]`, `client_id`).
- `V2__seed.sql`: base roles + SUPER operator `office@yammer.ro`
  (password seeded as MD5, upgraded to BCrypt on first login).
- `V3__Add_client_logo.sql`: `client.logo_object` (GCS object key).
- `V4__Super_admin_profile.sql`: name/phone/email for the seeded SUPER operator.
- `V5__Add_user_qr_token.sql`: `users.qr_token` (unique QR-login secret).
- `V6__Qr_token_uuid.sql`: `qr_token` re-created as a resettable UUID.
- `V7__Drop_barman_role.sql`: BARMAN role retired (removed from catalog and users).
- `V8__Add_location.sql`: `location` (client-owned).
- `V9__Add_user_location.sql`: `users.location_id` — home location, REQUIRED for
  client-scoped users (400 without one), must belong to the user's client
  (`UserService.resolveLocation`); null for SUPER.
- `V10__Add_order_point_type.sql`: `order_point_type` catalog, seeded SERVICE/BAR/TABLE.
- `V11__Add_vat_type.sql`: `vat_type` catalog, seeded 21.00/11.00/0.00.
- `V12__Add_order_point.sql`: `order_point` (location-owned, typed via
  `order_point_type`; self-FK `service_order_point_id`).
- `V13__Add_menu.sql`: `menu` (per location — no event) + `menu_item` tree
  (rich-text TEXT name, price, vat_type_id, image_object, combined, sort_order).
- `V14__Add_integration.sql`: `integration` (printers/cash registers per location).
- `V15__Add_payment_type.sql`: `payment_type` catalog (seeded CARD/CASH/PROTOCOL/PO)
  + `order_point.allow_multiple_users` / `payment_type_id`.
- `V16__Order_point_payment_types.sql`: single `payment_type_id` replaced with the
  multi-select `payment_type_ids UUID[]` set.
- `V17__Add_order_point_assignment.sql`: `order_point_assignment` (point↔user).
- `V18__Add_orders.sql`: `orders` + `order_item` (no payments yet).
- `V19__Add_products.sql`: `product` catalog + `menu_item.product_id` (price/vat/
  combined dropped off menu_item) + `recipe_component`.
- `V20__Menu_item_price.sql`: price moved BACK onto `menu_item` (per-menu pricing);
  dropped from `product`.
- `V21__Product_created_at.sql`: `product.created_at` (newest-first listing).
- `V22__Add_payment.sql`: `payment` + `order_item.payment_id` (settled-line link).
- `V23__Add_order_item_original_price.sql`: `order_item.original_price` — stamped
  on the unpaid remainder line when AMOUNT mode splits a unit's price.
- `V24__Add_table_session.sql`: `table_session` (one OPEN per point via partial
  unique index) + `orders.session_id` / `payment.session_id`; backfills an open
  session for every currently-assigned point, adopting its orders/payments.
- `V25__Add_self_pay_type.sql`: `self_pay_type` catalog (ONLINE/CHECK) +
  `order_point.self_pay_type_id`; DROPS the never-used `order_point.pay_later`.
- `V26__Customer_sessions_and_self_order.sql`: `customer_session` (device token
  joined to an OPEN table session, PENDING→APPROVED/DENIED, dies with the
  session) + `order_point.self_order_mode` (ALLOW/CONFIRM/DISALLOW, default
  CONFIRM) + `orders.customer_session_id`.
- `V27__Payment_status.sql`: `payment.status` (SUCCESS/PENDING/FAILED).
- `V28__Add_online_payment.sql`: `online_payment` parked-cart intents (Netopia)
  + seeds the ONLINE payment type.
- `V29__Location_active.sql`: `location.active` (default true).
- The old DB had 30 migrations; this baseline restarts at V1, so the API must run
  against a **fresh database volume**, not the old one.

## Deployment (GCP, Cloud Run)

Pushing to `main` runs `.github/workflows/deploy.yml`: `api/**` changes build
`api/Dockerfile` (Maven inside Docker), push to Artifact Registry
`europe-west1-docker.pkg.dev/yammer-order-app/yammer/api` and deploy Cloud Run
service **`yammer-api`** (europe-west1, min 1 / max 2 instances, 1 vCPU, 1 GiB).
Auth is Workload Identity Federation (pool `github`, bound to `Pezu/yammer-app`,
SA `gh-deployer`) — no GitHub secrets needed. A manual `workflow_dispatch` deploys both.

Runtime wiring (all set by the workflow — change it there, not in the console):
- DB: Cloud SQL Postgres 16 instance **`yammer-pg`** (db-g1-small, europe-west1), database
  `yammer`, user `yammer_app`, via the Cloud SQL socket factory; Flyway creates the schema.
  Password = Secret Manager `yammer-db-password`.
- Secrets: `jwt-secret`, `netopia-api-key`, `netopia-pos-signature` (sandbox).
- Media: GCS bucket `yammer-order-app-uploads` (runtime = default compute SA).
- `APP_URL` (QR-login links + Netopia notify) is a workflow env: `https://yammer.ro`,
  the main domain (rendezvous-app.ro / servioapp.ro are secondary aliases).
- Public URL: `https://yammer-api-926521730520.europe-west1.run.app`; custom hosts
  `api.yammer.ro` (main), `api.rendezvous-app.ro`, `api.servioapp.ro` (Cloud Run domain mappings).
- The old prod DB (schema V30) is NOT migrated — this deploy starts from an empty database.
  A dump of it lives at `~/Projects/yammer-old/db-dumps/`.
