# UX/UI audit — epic #1

Owner: `ux`. Scope: the four Yammer surfaces as they exist on `origin/develop` (`628c193`).

## 1. Method and limits

Code-level audit of `web/src/app` in full: every template, every component stylesheet,
`styles.scss`, `shared/`, `core/i18n/translations.ts`, `app.routes.ts`. No stack was running
during Phase 1 (the base ports belong to the human and no integration stack was up), so nothing
below rests on a screenshot — every finding names the file and, where it matters, the line.
A visual pass against an integration stack happens in Phase 4 (§9).

What this document is: the findings register, with a mini-spec for each finding that the team
accepts. What it is not: an architecture review (see `design.md`) or a defect list for the API.

**Severity**

| | meaning |
|---|---|
| **critical** | the user loses money, or a surface is unusable for its job |
| **major** | a state is missing, a flow dead-ends, money/status reads wrong, a house rule in `CLAUDE.md` §5 is broken, or an accessibility failure |
| **minor** | friction, inconsistency, or copy that is awkward but not misleading |
| **trivial** | cosmetic |

**Counts:** 63 findings — 0 critical, 29 major, 29 minor, 5 trivial.
Per surface: cross-cutting 12 (9 major), backoffice 9 (0 major), waiter 21 (9 major), service
board 6 (4 major), customer 15 (7 major).

Each finding is `UX-<surface><n>`: `C` cross-cutting, `B` backoffice, `W` waiter, `S` service
board, `Q` customer QR page.

---

## 2. Cross-cutting

### UX-C1 — Money is formatted `en-US` on both Romanian surfaces · **major**

`app.config.ts` registers no `LOCALE_ID` and no locale data, so every `| number: '1.2-2'` falls
back to `en-US`; the customer page formats explicitly with `'en-US'`
(`features/customer/customer-order-point-page.ts:1067`).

A 1 500,50 RON bill therefore renders **`1,500.50`** on a page whose language is Romanian, where
`,` is the decimal separator. A guest reading `1,500.50` can read it as one and a half lei, and a
waiter reading a `1,234.50` total on the stats page has the same ambiguity. It hurts customers and
waiters on every bill over 999 RON — common for a table of six.

**Spec.** One `money()` formatter in `shared/`, locale-driven by `I18nService.lang()`:
`ro` → `ro-RO` (`1.500,50`), `en` → `en-US` (`1,500.50`). Register `ro` locale data in
`app.config.ts`. The backoffice and service board keep `en-US` (they are English surfaces), so
reports and exports are unaffected. The customer page keeps its existing "drop a pointless `.00`"
rule: `42` → `42`, `42.5` → `42,50`.
Applies to: waiter table detail, pay sheet, order page, orders, payments, stats; customer menu,
cart bar, bill; payment-return.

### UX-C2 — The theme has no `--success` / `--warn`, so 29 off-token colours were invented · **major** (house rule)

`CLAUDE.md` §5: "theme tokens, no hard-coded hex". Counted across `web/src/app`: **29 distinct
hex colours in 23 files**, including **nine different greens** all meaning the same thing —
`#16a34a` (checkbox tick, order-points check), `#10b981` + `#0f9d63` (service board + waiter
Delivered button), `#28a745` (payment-return success), `#25b865`, `#1f7a3d`, `#1b5e20`, `#57a773`,
`#b7e4c7` (dashboard/report badges) — plus four ambers for "partial/warning" and literal
re-statements of existing tokens (`#3454d1` = `--primary` three times, `#283c50` = `--text`).

Consequence: "paid", "ready" and "ok" are a different green on every screen, and a future theme
change cannot reach them. It also makes the fiscal dot on the waiter Payments page (UX-W7) and the
service board's column colours unmaintainable.

**Spec.** Ask the architects to add to `styles.scss`: `--success`, `--success-soft`, `--warn`,
`--warn-soft`, `--surface` (today `#fff` is written literally ~60 times), `--overlay`
(`rgba(18,27,46,.45)`, written literally 6 times with one page using `rgba(0,0,0,.45)` instead) and
`--focus-ring` (UX-C4). Then replace the hex. Proposed values, taken from what the code already
uses most: `--success: #10b981`, `--success-soft: rgba(16,185,129,.12)`,
`--warn: #f59e0b`, `--warn-soft: rgba(245,158,11,.14)`, `--surface: #fff`,
`--overlay: rgba(18,27,46,.45)`. **This is a token change — it goes through the architects.**

### UX-C3 — An action that fails replaces the page content with an error · **major**

The pattern `@if (loading()) … @else if (error()) … @else <content>` is used on every waiter page,
but `error()` is also set by *action* handlers, not only by loaders. The failed action therefore
destroys the screen the waiter was working on:

- `waiter-table-detail-page.ts:582-583` — a refused payment sets `error()` **and** closes the pay
  sheet. The waiter is left on a full-screen "Could not record the payment" with no bill, no
  totals, no retry, and the whole selection (mode, picked items, tip) is gone. On a card terminal
  decline this is the single worst moment in the app.
- `waiter-orders-page.ts:224` — a failed *Delivered* swaps the order list for an error; nothing
  clears it until the tab is changed.
- `waiter-tables-page.ts:191-192` — the assign/unassign error is written to the page behind the
  picker overlay (see UX-W1).
- `waiter-approvals-page.ts` — a failed decision blanks both approval lists until the next 10 s
  poll repaints them.

**Spec.** Two channels, consistently:
- **Loader failures** keep the page-level `error()` state (the content genuinely does not exist).
- **Action failures** show a toast (`ToastService`) and, when they happen inside a sheet or
  dialog, an inline error line inside that sheet, above the action buttons. The sheet stays open
  and keeps its selection.
Specifically for the pay sheet: on failure keep `payOpen` true, render
`<p class="sheet-err" role="alert">{{ payError() }}</p>` directly above `.methods`, leave
`paySelection`/`tipMode` untouched so the waiter can hit the method button again.

### UX-C4 — Keyboard focus is unstyled; `combo-box` is not keyboard-operable · **major** (a11y)

`:focus-visible` appears in exactly two files, both for the custom checkbox. Buttons keep the
browser's default ring, which against a `--primary` fill (`.method`, `.ok`, `.order-btn`) and on
the square `--border` tiles is close to invisible. More seriously, `shared/combo-box.ts` — the
component every dropdown in the product is built on — handles **no keys at all**: no
`ArrowDown`/`ArrowUp`/`Home`/`End`, no `Enter`/`Space` to pick, no `Escape` (only the search input
has `keyup.escape`), focus is never moved into the menu or restored to the trigger, and the
trigger carries no `aria-haspopup` / `aria-expanded` / `aria-controls`. The listbox is a `<ul>` of
`<button>`s with no `role="listbox"`/`role="option"`/`aria-selected`.

Who it hurts: the backoffice is a desktop, keyboard-driven surface (`CLAUDE.md` asks for keyboard
paths), and the client/location/period combos are on the critical path of every report. A
keyboard-only admin can open a combo and then has to Tab through every option.

**Spec.**
1. `styles.scss`: one global rule —
   `:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }` with
   `--focus-ring: #3454d1` (= `--primary`), and keep the existing input `:focus` treatments.
2. `combo-box`: `role="combobox"` + `aria-haspopup="listbox"` + `aria-expanded` on the trigger,
   `aria-controls` → the menu; `role="listbox"` on `.combo-list`, `role="option"` +
   `aria-selected` on each option; `ArrowDown`/`ArrowUp` move an `activeIndex` (visually the
   existing `.active` style), `Enter`/`Space` select, `Escape` closes and returns focus to the
   trigger, `Tab` closes. Opening with the keyboard focuses the search box when it is shown
   (`searchable()`), otherwise the selected option. No behaviour change for pointer users.
3. `confirm-dialog`: `aria-labelledby` → the `h3`, `Escape` → `cancelled`, focus the confirm
   button on open (the cancel button when `danger()` is true, so a destructive dialog never
   confirms on a stray Enter), restore focus to the opener on close.

### UX-C5 — Accessible names on the two bilingual surfaces are English-only · **major** (house rule)

`CLAUDE.md` §5: "waiter + customer text through `t('key')`". Eleven accessible names and one toast
are hard-coded English, so a Romanian screen-reader or voice-control user gets English labels on
exactly the controls that have no visible text:

| file | literal |
|---|---|
| `waiter/waiter-page.ts:28` | `aria-label="Menu"` (hamburger) |
| `waiter/tables/waiter-tables-page.html:36` | `aria-label="Assign a table"` |
| `waiter/tables/waiter-order-page.html:2,12,74,76,91` | `Back`, `Clear search`, `Less`, `More`, `Remove one` |
| `customer/customer-order-point-page.ts:32,37,211,214` | `alt="logo"`, `aria-label="Menu"`, `Remove one`, `Add one` |
| `waiter/tables/waiter-table-detail-page.ts:119` | `toast.show(\`${this.name()} closed\`)` — visible text |

Also `printProforma`'s error surfaces `err?.error?.message` raw — an English server string shown
to a Romanian waiter.

**Spec.** New keys in §8. `alt` on the client logo becomes the client name
(`[alt]="op()!.clientName"` — see UX-Q9 for the API ask) and falls back to `''` for the
placeholder, since a decorative logo needs no alt text. The proforma error keeps the server
message only when the server provides a localized one; otherwise `t('detail.proformaFailed')`.

### UX-C6 — Legal pages do not exist; the links land on the staff login · **major**

`shared/site-footer.component.ts` renders six legal links (`/legal/termeni`, `/confidentialitate`,
`/livrare`, `/anulare`, `/gdpr`, `/contact`) and the customer drawer repeats them. `app.routes.ts`
has no `legal` route, so the wildcard `{ path: '**', redirectTo: 'login' }` sends the customer to
the **staff username/password form**. These links are the Netopia/ANPC compliance surface of a
card-accepting site; today a guest who taps "Politica de confidențialitate" is shown an internal
login.

**Spec.** A public `/legal/:doc` route rendering the six documents (static content, bilingual
headings, the site footer, a back link to the order point when `?op=` is present). Content is the
BA's/the human's to supply — `ux` specs only the shell. Until the content exists, the route must
exist and say so rather than bouncing to `/login`. Same for the wildcard: see UX-Q10.

### UX-C7 — The footer's legal links are 7px and the company line 6.7px · **major** (a11y)

`site-footer.component.ts:92` `.foot-legal a { font-size: 0.52rem }` → **7.3px**;
`:102` `.copy { font-size: 0.48rem }` → **6.7px**, both in `--muted`. These are legally required
links on every page including the customer's phone. At 7px with `line-height: 1.4` the tap target
is ~10px tall — far under the 24px floor of WCAG 2.5.8, and the text is simply not readable.

**Spec.** `.foot-legal a` → `0.75rem` (12px), `padding: 0.35rem 0.25rem` (≥24px target),
`color: var(--muted)`, underline on hover/focus; `.copy` → `0.7rem`. The footer grows by ~18px;
it is `margin-top: auto` so no layout breaks. The `.pay-label` (0.62rem/8.7px) goes to `0.7rem`.

### UX-C8 — The footer hard-codes one tenant's company · **major** (tenancy)

`site-footer.component.ts`: `© RENDEZVOUS EVENTS S.R.L. — CUI 41973877`, shown on every page of a
multi-tenant product. A guest scanning a QR code at a *Glo* table is told they are dealing with
Rendezvous Events, and the ANPC/Netopia identification is therefore wrong for every tenant but
one. Needs client legal data from the API — **question for the architects** (§7, Q1).

### UX-C9 — Wide tables overflow the page instead of scrolling in the panel · **minor**

No `overflow-x` anywhere in the backoffice. Order Points has **13 columns** (6 of them combos),
the dashboard's Tables widget has 8, Shifts has 8. Below ~1500px these either compress to
unreadable or push the document wider, which scrolls the **sidebar** off-screen with the content.

**Spec.** One line in `shared/styles/_crud-table.scss`: `.card-body { overflow-x: auto; }`. Every
page inherits it, the sidebar stops moving, and no page-specific CSS is needed.

### UX-C10 — Three hand-rolled copies of the combo instead of `combo-box` · **minor**

`combo-box` exists and is the house component, but: the SUPER client filter is hand-rolled markup
in `locations-page.html:6-26` (and its styles live in `_crud-table.scss` as `.client-combo`), the
in-table report filter is a second component (`reports/filter-combo.ts`), the page-size picker is
a third copy (`orders-report-page.html:106-120`), and the waiter order page hand-rolls a fourth
(`waiter-order-page.html:38-50`). Four dropdowns, four keyboard behaviours, four search rules
(the hand-rolled ones always show search; `combo-box` shows it only above 5 options). Fixing
UX-C4's keyboard support in `combo-box` would leave three dropdowns behind.

**Spec.** Replace the `locations` client combo, the page-size picker and the waiter menu switcher
with `app-combo-box`. `filter-combo` stays (it is a deliberately denser in-table variant) but
takes the same keyboard contract as `combo-box`.

### UX-C11 — Two native `<select>` elements · **major** (house rule)

`locations-page.html:67` and `:105` — the QR-frame picker, in both the draft row and the edit row.
`CLAUDE.md` §5 is explicit: `combo-box`, never a native `<select>`.

**Spec.** `app-combo-box [options]="qrTemplateOptions()" [allowEmpty]="true"
emptyLabel="Default frame" placeholder="Default frame"`. The font-size `<select>` inside
`rich-text-editor.ts` is a toolbar control of a third-party-style editor, not a form field; it
stays, but `menu-page.html:281` duplicates that whole toolbar inline instead of reusing
`rich-text-editor` — a **minor** consolidation item.

### UX-C12 — `100vh` without the `dvh` fallback on three surfaces · **trivial**

`backoffice-layout.scss:7`, `service-page.scss:3`, `payment-return-page.ts` use bare `100vh`,
while the waiter/customer shells correctly ladder `100vh` → `100dvh`. On a tablet with browser
chrome the service board's `height: 100vh` overflows by the toolbar height. Add the `100dvh`
line (and drop the duplicated `min-height: 100vh` lines in `waiter-page.ts` and
`customer-order-point-page.ts`).

---

## 3. Backoffice (desktop, English)

### UX-B1 — No loading state on any page · **minor**

Every CRUD page uses `loading()` only to suppress the empty row; the template never renders it
(`clients-page.html:107`). First paint is therefore a table with headers and nothing under them —
indistinguishable from "no data" — and rows pop in later. Same on all 20 pages, the reports and
the dashboard.

**Spec.** One row, reusing the existing `.empty` cell style:
`@if (loading()) { <tr><td [attr.colspan]="n" class="empty">Loading…</td></tr> }` placed above the
empty-state row, and the empty-state row keeps its `!loading()` guard. Copy: `Loading…` (the
backoffice is English). The reports' `select-prompt` pages are unaffected.

### UX-B2 — The Orders report shows raw status codes · **minor**

`orders-report-page.html:79-83`: the Paid column prints `PAID` / `PAR` / `NOT`. "PAR" and "NOT"
are not words; a manager reading the report has to guess.

**Spec.** `Paid` / `Partial` / `Not paid`, keeping the badge colours. The Status column already
maps through `statusLabel()`.

### UX-B3 — Report table rows select on click only · **minor** (a11y)

`orders-report-page.html:74` `<tr (click)="selectOrder(o)">` — no `tabindex`, no `role`, no key
handler. Picking an order to edit its unpaid quantities is impossible from the keyboard, on the
one backoffice screen that edits money.

**Spec.** Keep the row click, and make the first cell's content a real control:
`<td class="no-col"><button type="button" class="row-pick">{{ o.orderNo }}</button></td>` styled
flat, so Tab reaches it and Enter selects. `[attr.aria-current]="selected()?.id === o.id"` marks
the selection.

### UX-B4 — Sidebar scrolls away with the content · **minor**

`backoffice-layout.scss:10-15`: the sidebar is a static 250px flex child of a `min-height: 100vh`
row. A SUPER user has 4 expanded groups ≈ 20 links; on a 768px-tall laptop the Catalog group sits
below the fold and scrolling the table scrolls the navigation out of reach.

**Spec.** `.sidebar { position: sticky; top: 0; height: 100vh; height: 100dvh; overflow-y: auto; }`.

### UX-B5 — Collapsible groups expose no state to assistive tech · **minor** (a11y)

The four `.group-toggle` buttons carry no `aria-expanded`/`aria-controls`, and the parent group of
the active page gets no active styling (only the leaf `routerLinkActive`).

**Spec.** `[attr.aria-expanded]="configOpen()"` + `aria-controls` on each toggle;
`.group:has(.submenu-link.active) .group-toggle { color: var(--primary); }` so the open page's
group reads as current.

### UX-B6 — "Sales" and "Ordered" are the same number under two names · **minor**

`dashboard-page.html:46` labels the KPI tile **Sales** while the chart legend (`:76`) calls the
identical series **Ordered**. Pick one; `Ordered` matches the rest of the product (the waiter's
`unpaid`/`ordered` vocabulary and the Not-paid report).

### UX-B7 — `table(s)` · **trivial**

`shifts-page.html:36`: `<strong>{{ n }}</strong> table(s) still open`. Use two forms:
`1 table is still open — close it to cash up` / `{{n}} tables are still open — close them to cash
up`.

### UX-B8 — The sales chart has no non-visual alternative · **minor** (a11y)

`dashboard-page.html:85` `role="img" aria-label="Sales chart"` — the aria label carries no data,
and the `<rect>` tooltips are `<title>` only. The totals are already in the legend and the tiles,
so the loss is bounded.

**Spec.** `[attr.aria-label]` summarising the series instead of a static string, e.g.
`"Sales chart — ordered 4,210.00 RON, paid 3,980.00 RON over 14 buckets"`. No data table.

### UX-B9 — 13 editable columns on Order Points · **minor**

With the 250px sidebar, Order Points gives each of 13 columns ~79px, six of which hold combos;
headers are abbreviated to "Disc. %" and "Multiple users" wraps. UX-C9 stops it from breaking the
page, but the row is still hard to read and to edit.

**Spec.** No redesign in this epic. Two cheap steps: group the three boolean flags
(Self pay / Multiple users / Keep open) into one "Flags" column of small labelled ticks, and
right-align + fix the width of "Disc. %" with a `%` suffix in the cell. Anything further is a
story of its own.

---

## 4. Waiter phone app (RO default / EN)

### UX-W1 — The table picker swallows the reason an assignment was refused · **major**

`waiter-tables-page.html:8` renders `error()` inside `.page-body`; the picker is a fixed
bottom-sheet overlay at `z-index: 40` (`waiter-tables-page.scss:131`). `toggle()` sets exactly
that `error()` on failure (`:191`), including the 409 whose whole point is to explain that the
table still owes money (`tables.openRefused`). The waiter taps their table in the picker to leave
it, nothing happens, no message appears, the tile stays assigned — and the real reason ("take the
payment first") is painted on the page behind the sheet.

**Spec.** Render the error **inside** the picker, under `.picker-head`:
`@if (error()) { <p class="picker-err" role="alert">{{ error() }}</p> }` (style: `--danger` text on
`--danger` 10% fill, 0.85rem, full width of the sheet body). Clear it on the next successful
toggle and when the picker opens. Keep the page-level error for load failures only (UX-C3).

### UX-W2 — The tables board carries no state at all · **major**

The waiter's home screen — the screen the whole shift runs on — shows a square per table with the
name and an optional nickname, and nothing else. Not the outstanding amount, not whether food is
ready, not whether a customer is waiting for approval at that table, not how long the round has
been open. To find out who owes money the waiter must enter each table in turn and come back.

**Spec.** Each tile gains, under the name:
- the amount still due, as the dominant secondary line, in `--text` 0.85rem/700 — omitted entirely
  when nothing is due (an untouched table stays visually quiet);
- a right-aligned badge row: `N` in `--success-soft` when N orders are READY for that table, `!`
  in `--warn-soft` when a customer at that table is pending approval;
- the open-round age as `timeAgo` in `--muted` 0.7rem, bottom-left — only once the round is open.

A tile with nothing due and nothing pending looks exactly as it does today. Needs API data —
**architects, Q2.**

### UX-W3 — The board never refreshes · **major**

`waiter-tables-page.ts` loads once in the constructor. Nothing polls, nothing subscribes to
`/api/ws/orders` (the service board does). A split created by a colleague, a new customer order,
a payment taken on a shared table — none of it appears until the waiter leaves and returns, or
uses the top-bar refresh, which is a `window.location.reload()` (`waiter-page.ts:refreshPage`).
With UX-W2 the staleness becomes actively misleading: a tile would show an amount that is already
paid.

**Spec.** Refresh `loadAssigned()` on a 15 s interval (aligned with the approvals poll already in
the shell) and on `visibilitychange` → visible, replacing tiles in place with no loading flash —
the picker already does exactly this (`loadBoard()`). If the architects prefer the WebSocket, the
same refresh hangs off an `ORDER_*`/`PAYMENT_*` message; polling is the safety net either way.

### UX-W4 — Split is a 30px button inside the tile's tap area, with no confirmation · **major**

`waiter-tables-page.html:19-30` + `.scss:216`: the split button is 30×30px, absolutely positioned
6px from the top-right corner **over** the tile that opens the table. A thumb aiming at the top of
a 96px tile creates `T12.2` — a new order point, assigned to the waiter, with its own bill — and
there is no undo anywhere in the UI.

**Spec.** 44×44px target; move it to the **bottom-right** corner, away from the natural tap zone
of a tile the waiter opens dozens of times a shift; and route it through `confirm-dialog`:
`tables.splitConfirmTitle` / `tables.splitConfirmBody` / confirm `tables.split` (copy in §8). The
same 44px floor applies to `.close-btn` (32px) and the shell's `.refresh` / `.burger` (36px).

### UX-W5 — "Close table" ends the round in one tap · **major**

`waiter-table-detail-page.html:112` — `Close table` sits in the sticky footer beside `Pay` and
`Order`, same size, no confirmation, and it drops every assignment and ends the table round. A
mis-tap costs the waiter the table; re-assigning starts a *new* round.

**Spec.** `confirm-dialog`, `danger` false (it is not destructive, but it is final):
`detail.closeConfirmTitle` / `detail.closeConfirmBody` / confirm `detail.closeTable`, cancel
`common.cancel`. Also: the success toast is the English literal `` `${name} closed` ``
(`:119`) — becomes `t('detail.closedToast', { name })`.

### UX-W6 — The pay sheet dismisses on a stray touch and keeps no inline error · **major**

`waiter-table-detail-page.html:118` — `(mousedown)="closePay()"` on the overlay. On touch,
`mousedown` fires from a tap anywhere outside the sheet, including the sliver of overlay above it,
and a partial selection built item by item disappears with no warning. Combined with UX-C3 (the
sheet also closes itself on a payment failure), the sheet loses work in two different ways.

**Spec.** Remove the overlay dismissal; the sheet closes via its X button only (it is a
money-entry form with unsaved state). Keep `role="dialog" aria-modal="true"`, add
`aria-labelledby` → the `h3`, close on `Escape`. Inline error line per UX-C3. The same overlay
pattern on the waiter order page's pay sheet (`waiter-order-page.html:138`) changes with it.

### UX-W7 — Fiscal status is a coloured dot and nothing else · **major** (a11y + clarity)

`waiter-payments-page.ts:58-66`: the Status column is a 10px dot whose only label is
`[attr.title]="p.fiscalStatus"` — a `title` tooltip, which a phone never shows, carrying a raw
English enum (`UNKNOWN`). Status is encoded in colour alone (WCAG 1.4.1) and has no accessible
name. The `payments.success|pending|failed|unknown` translations already exist; the table does not
use them.

**Spec.** Dot + text label in the same cell: dot (`--success` / `--warn` / `--danger` /
`--muted`), then `t('payments.' + status.toLowerCase())` at 0.72rem beside it, and
`[attr.aria-label]` with the same text on the cell. The FAILED/UNKNOWN explanation rows stay as
they are — they are good.

### UX-W8 — PENDING receipts never resolve on screen · **major**

A CASH/CARD receipt sits `PENDING` until the bridge answers or the api's 180 s sweeper fails it,
but `waiter-payments-page.ts` loads once. The waiter watches a pending dot that will never change,
then walks away not knowing whether the receipt printed.

**Spec.** While `rows()` contains any `PENDING`, poll `myPayments()` every 10 s; stop when none
remain or on destroy. After a `retry()` the page already reloads after 2.5 s — on that reload,
toast the outcome: `payments.retryIssued` when the row is now SUCCESS, `payments.retryStillFailed`
when it is still FAILED (copy in §8), instead of silently repainting.

### UX-W9 — The Payments page has no total · **minor**

Five columns of amounts and tips, no sum. "How much have I taken today" is the reason a waiter
opens this page, and it is answered one screen away (Statistics).

**Spec.** A sticky `<tfoot>` row in the existing table style: `t('payments.total')` across the
table/type columns, then the summed amount and tip of `visibleRows()` (so it follows the filter),
`font-weight: 800`, `font-variant-numeric: tabular-nums`, top border `--border`.

### UX-W10 — Ready food does not reach the waiter · **major**

`waiter-orders-page.ts` fetches on navigation only. The service board marks an order READY and
pushes it over `/api/ws/orders`; the waiter's Orders page learns nothing until they happen to open
it. The shell polls *approvals* every 15 s and shows a red badge for them — the same mechanism is
missing for the thing that gets food to the table.

**Spec.** Poll `myOrders('READY')` count in the waiter shell on the existing 15 s tick and show a
second top-bar badge — `--success` filled, the READY count, tapping goes to
`/waiter/orders?tab=READY` — next to the red approvals badge. On the Orders page itself, refresh
the open tab on the same interval and on `visibilitychange`. Needs a cheap count endpoint or the
existing list — **architects, Q3.**

### UX-W11 — Nothing says which page you are on · **minor**

The shell's top bar shows logo, name, refresh, hamburger. Orders, Payments and Statistics render
only a right-aligned combo; nothing names the page, and the hamburger marks no current item. After
a glance away mid-shift, Orders and Payments look alike (both are a combo over a list).

**Spec.** The page title goes in the top bar, left-aligned after the logo, 0.95rem/700,
`t()`-driven per route (`menu.tables` / `menu.approvals` / `menu.orders` / `menu.payments` /
`menu.stats`); the table detail and order pages keep their own back-header and set no shell title.
In the hamburger, the current route's item gets `--primary` text + `--primary` 8% fill, matching
`.combo-option.active`.

### UX-W12 — The partial-pay picker is drag-first · **minor**

`detail.pay` → "Select items" builds a payment by dragging chips between Bill and Paying, with
tap = move one and a 500 ms long-press = type a quantity (`waiter-table-detail-page.ts:
onChipPointerDown`). Three gestures on the same 40px chip, one-handed, in a noisy room; a tap
intended as a long-press silently changes the amount by one unit. It was ported deliberately from
the old app, so this is a proposal rather than a defect.

**Spec.** Keep drag and tap exactly as they are, and add a `−`/`+` stepper with the quantity
between them to each chip in the Paying zone (the house stepper already exists on the waiter order
page and the orders report). The long-press stays for typing a large quantity. Hint copy
(`pay.partialHint`) gains the stepper and loses nothing.

### UX-W13 — A split line's price pair has no explanation · **minor**

`waiter-table-detail-page.html:30` shows `4.50 / 9.00` next to a half-circle icon on a partially
paid unit. Correct per the money rules, and opaque: nothing on screen says the second number is
the original price.

**Spec.** `[title]` and `[attr.aria-label]` on the tag → `t('detail.partialTag')`
("Partially paid — {paid} of {full}"), copy in §8. The customer page already does the `title` half
of this (`cust.partial`), so the two surfaces end up consistent.

### UX-W14 — A typed amount over the bill is silently clamped · **minor**

In AMOUNT mode the input accepts `500` on a 50 RON bill; `grossTotal()` clamps to the amount due,
so the sum rows below read 50.00 while the field still reads 500. Nothing says why.

**Spec.** `[max]` on the input plus, when the typed value exceeds what is due, a hint line under
it: `t('pay.amountCapped', { max })` ("Maximum due is {max} RON"). The clamp itself does not
change.

### UX-W15 — Deny destroys a customer's order with no confirmation · **minor**

`waiter-approvals-page.ts`: Deny deletes the order outright and sits immediately left of Approve,
same size, both `[disabled]="busy()"`. A mis-tap on a phone cannot be undone and the customer is
never told why their order vanished.

**Spec.** `confirm-dialog` on Deny only (`approvals.denyConfirmTitle` /
`approvals.denyConfirmBody`, `danger` true). Approve stays one tap — it is the happy path and it
is reversible by the bill.

### UX-W16 — The waiter's cart is lost on navigation · **minor**

`waiter-order-page.ts` keeps `cart` in a component signal. The customer page persists its cart in
`sessionStorage`; the waiter's does not, and the page's own back button at the menu root navigates
to the table page and destroys it. A half-built round for a table of eight is gone.

**Spec.** Persist to `sessionStorage` under `yammer.waiter-cart.<orderPointId>`, same effect-based
write the customer page uses, cleared after a successful `placeOrder`. No UI change.

### UX-W17 — Search with no matches shows nothing · **minor**

`waiter-order-page.html:21` opens the results dropdown only `@if (searchGroups().length > 0)`. A
typo gives no dropdown, no message — the waiter cannot tell a misspelling from a missing product.

**Spec.** Render the dropdown whenever the query is non-empty, with
`<p class="combo-empty">{{ t('order.noMatches') }}</p>` when there are no groups.

### UX-W18 — "Payment recorded" before the receipt exists · **minor**

`pay()` toasts `pay.recorded` on the API's 200. At that moment the fiscal receipt is `PENDING` and
may still fail. The copy promises more than has happened, and nothing points the waiter at the
Payments page where a FAILED receipt would need their retry.

**Spec.** Copy only: `pay.recorded` becomes "Payment recorded — receipt printing" /
"Plată înregistrată — se tipărește bonul". Nothing is added to the flow; the Payments page plus
UX-W8 does the follow-up.

### UX-W19 — Statistics shows money without a unit · **minor**

Every other waiter screen writes `RON`; `waiter-stats-page.ts` prints bare numbers in every row of
every card.

**Spec.** `RON` on the two `.row.total` rows and the `.row.unpaid` row of each card (not on every
line — that would be noise at this density).

### UX-W20 — `<span role="button">` nested inside a `<button>` · **minor** (a11y)

`waiter-order-page.html:91`: the quantity-decrement badge is a `role="button"` span inside the
`.tile` button. Interactive content inside interactive content: not focusable, not reachable by
keyboard, and an invalid accessibility tree.

**Spec.** The same `.tile-wrap` treatment the tables board already uses for its split button:
sibling `<button>` positioned over the tile corner, not a child. Keeps the visual result.

### UX-W21 — Toasts are not announced and can sit on the action bar · **trivial**

`waiter-page.ts` renders the toast as a plain `div` at `bottom: 1.25rem` — no `role="status"`, no
`aria-live`, and it overlaps the table detail's sticky footer buttons.

**Spec.** `role="status" aria-live="polite"` on the toast, and `bottom: calc(1.25rem + 64px)`
when the host page has an action bar (a `--toast-offset` custom property the page can set).

---

## 5. Service board (kitchen/bar, English, read at a distance)

### UX-S1 — The type scale is a phone's, not a wall display's · **major**

`service-page.scss`: order items **0.82rem (11.5px)**, order point name 0.95rem, order number
0.8rem, elapsed time 0.72rem (10px). A cook reads this board from 1.5–3 m, often over a counter.
11px line items cannot be read at that distance, which is the entire purpose of the surface.

**Spec.** A distance scale for the board only: items `1.05rem` (≈17px) with `line-height: 1.6`,
quantity `1.05rem/800`, order point `1.15rem/800`, order number `1rem/800`, elapsed time
`0.9rem`, column title `1.1rem/800` uppercase. Card padding grows to `0.85rem 1rem`; the two
columns stay. No new settings, no density toggle.

### UX-S2 — Nothing marks a ticket as new, and nothing marks one as late · **major**

Every card looks identical. An order that arrived 3 seconds ago and one that has been sitting 25
minutes are distinguished only by a 10px grey "25 min ago". In a kitchen that is the difference
between a service and a complaint.

**Spec.** Two purely visual states, computed from `createdAt` on the existing render:
- **new** — under 60 s in ORDERED: 2px `--primary` left border on the card and the card
  background at `--primary` 4%, fading out by a CSS transition once the card re-renders older.
- **late** — the elapsed-time chip turns `--warn` text on `--warn-soft` at 10 minutes and
  `--danger` text on `--danger` 10% at 20 minutes, in the ORDERED column only (a READY ticket is
  the waiter's problem, and UX-W10 is what fixes that).
Thresholds are constants in `service-page.ts`, not settings.

### UX-S3 — No loading state and no empty column · **major**

`loading()` is maintained in `service-page.ts` and **never used in the template**, and the columns
have no `@empty`. During the initial fetch and whenever a column is empty the board shows two
blank boxes. A blank board and a broken board look the same, on a screen nobody is standing in
front of.

**Spec.** `@if (loading()) { <p class="col-state">Loading…</p> }` inside each `.col-body`, and
`@empty { <p class="col-state">Nothing ordered</p> }` / `Nothing ready` per column, in `--muted`
at `1rem`, vertically centred. The existing `error()` banner stays.

### UX-S4 — A mis-tapped "Delivered" loses the ticket silently · **major**

`deliver()` optimistically removes the card and `DELIVERED` leaves the board for good. The ✓
button sits 8px from the "move back" button on a touch screen. After a mis-tap the ticket is gone
from the board *and* from the waiter's Orders page, and nothing on the board can bring it back.

**Spec.** A board toast (the shell has none; add the same `ToastService` pattern) reading
`Order #124 delivered` with an **Undo** action for 6 s, which calls `updateStatus(id, 'READY')`
and re-inserts the card. Whether the API accepts `DELIVERED → READY` is **architects, Q4**; if it
does not, fall back to `confirm-dialog` on Delivered ("Mark #124 delivered?").

### UX-S5 — No per-column counts · **minor**

The column head shows only its label. A cook cannot tell at a glance whether there are 3 or 13
tickets below the fold.

**Spec.** `Ordered · 7` in the column head, the count in `--muted` after a middot.

### UX-S6 — Action buttons are icon-only with `title` and no accessible name · **minor** (a11y)

`service-page.html:102-112`: `title="Mark ready"`, `title="Move back"`, `title="Delivered"`, no
`aria-label`. Also the station button (`:6`) and `Choose station` fall back to a literal.

**Spec.** `aria-label` mirroring each `title`, including the order number
(`[attr.aria-label]="'Mark order #' + o.orderNo + ' ready'"`) so the three identical buttons on
adjacent cards are distinguishable. English, as the surface is.

---

## 6. Customer QR page (RO default / EN)

### UX-Q1 — There is no cart to review before ordering · **major**

Items are incremented in place; the footer shows "3 items · 84" and `Place order`. There is no
screen or sheet that lists what is in the cart. To check an order before sending it the guest must
scroll the whole menu hunting for quantity badges. The waiter app has a Summary step for exactly
this; the paying customer does not.

**Spec.** The cart bar becomes tappable (the counter/total area, not the button): it opens a
bottom sheet `cust.cartTitle` with one row per line — name, unit price, the existing `−`/`+`
stepper at the enlarged size (UX-Q4), line total — then `cust.totalDue` and the primary
`cust.placeOrder` button; X to close; `role="dialog" aria-modal="true"`, `Escape` closes. The
`Place order` button in the bar keeps working directly so the one-tap path is unchanged.

### UX-Q2 — A card payment starts with no confirmation and loses the cart · **major**

`placeOrder()` (`customer-order-point-page.ts:1125-1137`) clears the cart, then
`window.location.href = result.paymentUrl`. The guest is sent to a bank page without ever seeing
"you are about to pay X RON", and the button they pressed said "Place order", not "Pay". If they
abandon the gateway and come back, the cart has already been emptied and the whole order must be
re-picked.

**Spec.**
1. When the order point self-pays `ONLINE`, the cart-bar button reads `cust.payAndOrder`
   ("Pay {total} RON" / "Plătește {total} RON") instead of `cust.placeOrder`.
2. It opens the cart sheet (UX-Q1) in a confirm state: the lines, `cust.totalDue`, a line
   `cust.onlinePayNote` ("You will be taken to the secure Netopia page to pay by card." /
   "Vei fi dus la pagina securizată Netopia pentru plata cu cardul."), then the pay button.
3. Clear the cart **only** after the gateway confirms — i.e. on the payment-return page when
   `status === 'PAID'`, keyed by the order point id. An abandoned payment therefore returns the
   guest to an intact cart.
Whether the point self-pays ONLINE must be readable on the public order-point payload —
**architects, Q5.**

### UX-Q3 — The menu's own type scale is too small, and the price is the quietest thing on the row · **major**

`customer-order-point-page.ts:586` item name **13px**, `:649` description **12px**, `:656` price
**12px in `--muted`**. This is a restaurant menu, read by guests of every age, often in low light.
The price — the one number a guest looks for — is rendered smaller than the name and in the
muted grey reserved for secondary text.

**Spec.** Name `15px/600` in `--text`; price `15px/700` in `--text` (not `--muted`), with `RON`
kept at `12px` beside it in `--muted`; description `13px/1.4` in `--muted`; category heading
`17px/800`; quick-nav chip `14px`. Everything else (layout, image, spacing) unchanged.

### UX-Q4 — The ordering buttons are 24×24px · **major** (a11y)

`:674-676` `.qty-btn { width: 24px; height: 24px }` with an 8px gap. These are the controls that
make the sale, on a phone, and they sit at the absolute floor of WCAG 2.5.8 with no margin for a
thumb. Mis-taps add the wrong quantity or hit the neighbouring row.

**Spec.** 40×40px, gap `10px`, `-webkit-tap-highlight-color: transparent`, quantity value
`min-width: 24px` at `16px/700`. The `.item-side` column widens accordingly; the item row grows by
~16px, which the 15px type (UX-Q3) absorbs.

### UX-Q5 — The order confirmation renders where the guest is not looking · **major**

`placed` draws a banner at the **top of `main`** (`:81-85`), while the guest taps `Place order` in
the fixed bar at the **bottom** of a long scrolled menu. On success the cart bar disappears
(count 0) and nothing else visibly changes: the guest cannot tell whether the order was sent, and
re-taps are a real risk.

**Spec.** Replace the inline banner with a centred confirmation sheet: ✓ in `--success`,
`cust.orderSent` or `cust.orderSentApproval` as the heading, `cust.orderSentBody` ("The waiter has
your order." / "Ospătarul are comanda ta."), and two buttons — `cust.viewOrder` (switches to the
Order/bill view) and `common.close`. `role="dialog"`, `aria-modal`, `Escape`/backdrop closes.
Error (`orderError`) stays inline where it is, but moves into the cart sheet when the sheet is
open.

### UX-Q6 — Picking the wrong half of a split table is unrecoverable · **major**

`slotChoice` blocks the page until the guest picks `T12.1` or `T12.2`; the answer is written to
`localStorage` (`slotChoiceKey`) and `knownSlot()` honours it on every later scan. The dialog has
no cancel, nothing afterwards displays which half was chosen, and nothing can change it. A guest
who guesses wrong puts their orders — and their money — on the neighbouring party's bill.

**Spec.**
1. The topbar's `.table-no` shows the chosen slot name as it already does, and gains a small
   `cust.changeTable` text button beside it, visible only when `op().slots.length > 1`, which
   clears the stored choice and re-opens the chooser.
2. The chooser gets a second line: `cust.pickSlotHelp` ("Not sure? Ask your waiter which part of
   the table you are at." / "Nu știi? Întreabă ospătarul la ce parte a mesei stai.").
3. Changing the slot must not strand the guest's existing session — **architects, Q6** (what
   happens to an approved session and its orders on the abandoned slot).

### UX-Q7 — "Denied" is a dead end · **minor**

`cust.denied` — "The waiter declined access at this table." — and then nothing. No retry, no
guidance. A waiter who tapped Deny by accident (UX-W15) leaves the guest with a terminal message
and a menu they cannot order from.

**Spec.** Under the message, a `cust.askAgain` button ("Ask again" / "Cere din nou") that re-runs
`join()`, returning the guest to the PENDING banner. Rate-limited to one request per 60 s on the
client so Deny stays meaningful. Whether a re-join after DENIED is allowed — **architects, Q7.**

### UX-Q8 — The bill view never updates · **minor**

The Order view loads once. A guest watching their bill while the waiter takes a payment at the
table sees a stale "To pay" until they reload the page.

**Spec.** While `view() === 'orders'`, poll the bill every 10 s; stop on view change and on
destroy. `pendingApproval`/`billNeedsApproval` states are unaffected.

### UX-Q9 — The logo has no accessible name and no fallback text · **minor** (a11y)

`:32` `<img class="logo" [src]="logoSrc()" alt="logo" />`. "logo" is not a name; a guest using a
screen reader learns nothing about whose venue they are in, and the placeholder logo is decorative
but still announced.

**Spec.** `[alt]="op()?.clientName ?? ''"`. Needs the client name on the public payload —
**architects, Q1** (same ask as UX-C8).

### UX-Q10 — A bad QR code shows the staff login form · **major**

`{ path: '**', redirectTo: 'login' }`. A smudged, mis-printed or outdated QR code — and any
`/customer/order-point/<unknown-id>` — lands the guest on Yammer's **staff username and password
form**. They will assume they must log in to order, and some will try.

**Spec.** A public not-found page, bilingual, no login affordance: `nf.title` ("This code is not
working" / "Codul nu funcționează"), `nf.body` ("Ask your waiter for a menu, or scan the code
again." / "Cere ospătarului meniul sau scanează din nou codul."), the site footer, no buttons.
`/customer/**` and an unresolvable order point route here; the wildcard for non-customer URLs may
keep redirecting to `/login`.

### UX-Q11 — The customer menu has no search · **minor**

The waiter app searches products across all menus; the guest gets quick-nav chips for top-level
categories only. On a 120-item menu on a phone, finding "Aperol" is a long scroll.

**Spec.** Not in this epic unless the BA wants it — recorded as an improvement, not a defect. If
accepted: the existing search field pattern from the waiter order page, in the sticky topbar,
`t('cust.search')` ("Search the menu…" / "Caută în meniu…"), filtering the flat `products()` list
the page already computes, with `order.noMatches`-style empty copy.

### UX-Q12 — Language sits in the middle of the navigation · **trivial**

Drawer order is Menu · Language · Order · legal links. The language control splits the two
navigation items.

**Spec.** Menu · Order · (rule) · Language · (rule) · legal links.

### UX-Q13 — The ONLINE wait screen does not tell the guest to stay put · **minor**

`payment-return-page` polls for up to 2 minutes behind "Checking your payment… / This usually
takes a few seconds." Nothing warns against closing the tab, which is the one action that loses
them the confirmation.

**Spec.** `ret.checkingText` → "This usually takes a few seconds. Please keep this page open." /
"Durează de obicei câteva secunde. Te rugăm să nu închizi pagina."

### UX-Q14 — The payment-confirmed screen shows no amount · **minor**

"Payment confirmed" and a tick. No amount, no reference, nothing the guest can show a waiter or
check against their card statement.

**Spec.** Under the heading: `ret.paidAmount` ("Paid {amount} RON") and, when the API returns one,
the order number as `ret.paidOrder` ("Order #{no}"). Plus a `cust.order` link to the bill view
beside `ret.back`. Needs amount + order number on `/public/payments/{ref}/status` —
**architects, Q8.**

### UX-Q15 — Legal links are Romanian-only on the English page · **trivial**

`LEGAL_LINKS` labels are fixed Romanian strings in both the footer and the customer drawer. The
documents themselves are Romanian legal texts, so the labels are arguably correct; recorded so the
decision is on the record rather than accidental. If translated, EN labels go in the same
`LEGAL_LINKS` structure keyed by language.

---

## 7. Questions for the architects

Each of these gates a state specced above; `ux` needs the answer before the matching story goes
`stage:ready`.

| # | For | Question |
|---|---|---|
| Q1 | UX-C8, UX-Q9 | Can the public order-point payload carry the client's **display name and legal line** (company, CUI), so the footer and the logo `alt` are per tenant instead of hard-coded Rendezvous? |
| Q2 | UX-W2 | Can `GET /order-points/assigned` return, per point: `unpaidTotal`, `readyOrderCount`, `pendingApprovalCount`, `roundOpenedAt`? Without these the tiles cannot carry state. |
| Q3 | UX-W10 | Is there (or can there be) a cheap count for "READY orders at my tables", or should the shell poll the existing list? Alternatively, should the waiter shell subscribe to `/api/ws/orders`? |
| Q4 | UX-S4 | Does the API accept `DELIVERED → READY`? If not, Delivered needs a confirm dialog instead of an undo. |
| Q5 | UX-Q2 | Does the public order-point payload say whether the point self-pays `ONLINE` (and with what total), so the button can read "Pay X RON" before the redirect? |
| Q6 | UX-Q6 | What happens to an approved customer session and its placed orders when a device changes its split-table slot? Is a second join allowed? |
| Q7 | UX-Q7 | May a DENIED device re-request access (new PENDING), or is Deny terminal for that device/token? |
| Q8 | UX-Q14 | Can `/public/payments/{ref}/status` return the paid amount and the resulting order number? |
| Q9 | UX-W18 | Does the pay response expose the fiscal status, so the toast can distinguish "receipt printing" from "receipt issued"? |

Token change for UX-C2 and the `--focus-ring` in UX-C4 also needs an architect decision.

---

## 8. Copy to add (RO + EN)

New `translations.ts` entries, exactly as the frontend should add them. EN is the reference
dictionary; every key must exist in RO.

| key | EN | RO |
|---|---|---|
| `common.back` | `Back` | `Înapoi` |
| `common.openMenu` | `Menu` | `Meniu` |
| `common.clear` | `Clear` | `Golește` |
| `common.less` | `Less` | `Mai puțin` |
| `common.more` | `More` | `Mai mult` |
| `common.removeOne` | `Remove one` | `Scade unul` |
| `common.addOne` | `Add one` | `Adaugă unul` |
| `tables.assignOpen` | `Assign a table` | `Alege o masă` |
| `tables.splitConfirmTitle` | `Split this table?` | `Împarți masa?` |
| `tables.splitConfirmBody` | `A new part of {name} is created and assigned to you. It gets its own bill.` | `Se creează o parte nouă a mesei {name}, alocată ție. Va avea notă separată.` |
| `detail.closeConfirmTitle` | `Close this table?` | `Închizi masa?` |
| `detail.closeConfirmBody` | `{name} is freed and the round ends. Everything is paid.` | `Masa {name} se eliberează și runda se încheie. Totul este plătit.` |
| `detail.closedToast` | `{name} closed` | `Masa {name} a fost închisă` |
| `detail.partialTag` | `Partially paid — {paid} of {full}` | `Plătit parțial — {paid} din {full}` |
| `pay.amountCapped` | `Maximum due is {max} RON` | `Maxim de plată: {max} RON` |
| `pay.recorded` *(replaces)* | `Payment recorded — receipt printing` | `Plată înregistrată — se tipărește bonul` |
| `pay.failedInline` | `The payment was not recorded. Check the terminal and try again.` | `Plata nu a fost înregistrată. Verifică terminalul și încearcă din nou.` |
| `payments.total` | `Total` | `Total` |
| `payments.retryIssued` | `Receipt issued` | `Bonul a fost emis` |
| `payments.retryStillFailed` | `Still not issued — see a manager` | `Tot neemis — anunță un manager` |
| `payments.emptyFiltered` | `No payments with this status.` | `Nicio plată cu această stare.` |
| `orders.emptyOrdered` *(replaces)* | `Nothing being prepared.` | `Nimic în pregătire.` |
| `order.noMatches` | `No product matches that.` | `Niciun produs nu se potrivește.` |
| `approvals.denyConfirmTitle` | `Deny this order?` | `Refuzi comanda?` |
| `approvals.denyConfirmBody` | `The order is deleted and the customer is not told why.` | `Comanda se șterge și clientul nu află motivul.` |
| `cust.cartTitle` | `Your order` | `Comanda ta` |
| `cust.payAndOrder` | `Pay {total} RON` | `Plătește {total} RON` |
| `cust.onlinePayNote` | `You will be taken to the secure Netopia page to pay by card.` | `Vei fi dus la pagina securizată Netopia pentru plata cu cardul.` |
| `cust.orderSentBody` | `The waiter has your order.` | `Ospătarul are comanda ta.` |
| `cust.viewOrder` | `See my order` | `Vezi comanda` |
| `cust.changeTable` | `Change` | `Schimbă` |
| `cust.pickSlotHelp` | `Not sure? Ask your waiter which part of the table you are at.` | `Nu știi? Întreabă ospătarul la ce parte a mesei stai.` |
| `cust.askAgain` | `Ask again` | `Cere din nou` |
| `cust.search` | `Search the menu…` | `Caută în meniu…` |
| `ret.checkingText` *(replaces)* | `This usually takes a few seconds. Please keep this page open.` | `Durează de obicei câteva secunde. Te rugăm să nu închizi pagina.` |
| `ret.paidAmount` | `Paid {amount} RON` | `Plătit {amount} RON` |
| `ret.paidOrder` | `Order #{no}` | `Comanda #{no}` |
| `nf.title` | `This code is not working` | `Codul nu funcționează` |
| `nf.body` | `Ask your waiter for a menu, or scan the code again.` | `Cere ospătarului meniul sau scanează din nou codul.` |

Existing entries to correct:

| key | problem | fix |
|---|---|---|
| `stats.comped` | EN `Comped` (jargon), RO identical to `stats.protocol` | EN → `Protocol / PO`, RO stays `Protocol / PO` |
| `pay.tip` / `payments.tip` / `stats.tip` | RO alternates `Bacșiș` and `Tips` for the same thing | all three RO → `Bacșiș` |
| `pay.customPct` / `pay.percent` | both RO `Procent` for two different controls | `pay.customPct` RO → `Procent %`, `pay.percent` RO → `Procent` |
| `detail.proforma` | EN `Proforma` (noun) vs RO `Printează nota` (verb) | EN → `Print bill`, RO stays |
| `tables.assignedMany` | RO `{n} alocați` — masculine, for feminine `mese` | RO → `{n} alocate` |

Backoffice copy (English, no `t()`): `Loading…` (UX-B1), `Paid` / `Partial` / `Not paid`
(UX-B2), `Ordered` instead of `Sales` (UX-B6), `1 table is still open — close it to cash up` /
`{n} tables are still open — close them to cash up` (UX-B7).

---

## 9. Accessibility summary

The failures worth tracking separately, all specced above:

| | finding |
|---|---|
| WCAG 1.4.1 (colour alone) | UX-W7 fiscal status |
| WCAG 1.4.4 (resize) | login locks the viewport (`login.ts` sets `user-scalable=no`) — accepted for a POS login, recorded as a deliberate exception |
| WCAG 2.1.1 (keyboard) | UX-C4 combo-box, UX-B3 report rows, UX-W20 nested interactive |
| WCAG 2.4.7 (focus visible) | UX-C4 |
| WCAG 2.5.8 (target size) | UX-Q4 24px steppers, UX-W4 30px split / 32px close / 36px shell buttons, UX-C7 7px legal links |
| WCAG 4.1.2 (name, role, value) | UX-C4 combo + dialogs, UX-C5 English-only names, UX-Q9 logo alt, UX-S6 icon buttons, UX-B5 `aria-expanded` |
| Live regions | UX-W21 toast, UX-S4 board toast |

Contrast against the tokens is fine where it was checked: `--muted` #64748b on white is 4.76:1
(passes AA for body text), `--primary` #3454d1 on white 6.3:1, white on `--primary` 6.3:1, white
on `--danger` #ea4d4d 3.3:1 — the last one **fails** AA for normal text, so white text on a
`--danger` fill (the approvals badge, `confirm-dialog`'s danger button, `.method` variants) should
use `font-weight: 700` at ≥18.66px to qualify as large text, or `--danger` should darken to
`#d93a3a` (4.0:1) / `#c62828` (5.0:1). Recorded as part of the UX-C2 token conversation.

## 10. Suggested story split

Phase-2 stories, ordered by value per unit of work. Each is independently shippable.

1. **Waiter error handling and state loss** — UX-C3, UX-W1, UX-W6, UX-W14, UX-W18. No API work.
2. **Customer menu legibility and the cart** — UX-Q1, UX-Q3, UX-Q4, UX-Q5. No API work.
3. **Money formatting** — UX-C1. One shared helper, touches every surface.
4. **Theme tokens + focus ring + combo-box keyboard** — UX-C2, UX-C4, UX-C10, UX-C11. Needs the
   architects' token decision first.
5. **Service board for its actual viewing distance** — UX-S1, UX-S2, UX-S3, UX-S5, UX-S6.
6. **Compliance and dead ends** — UX-C6, UX-C7, UX-C8, UX-Q10. Needs Q1 and legal content.
7. **Waiter board that tells the truth** — UX-W2, UX-W3, UX-W10. Needs Q2 and Q3.
8. **Fiscal visibility for the waiter** — UX-W7, UX-W8, UX-W9.
9. **Destructive actions get a confirmation** — UX-W4, UX-W5, UX-W15, UX-S4 (Q4).
10. **i18n coverage and copy corrections** — UX-C5 plus the §8 corrections table.
11. **Backoffice polish** — UX-B1, UX-B2, UX-B3, UX-B4, UX-B5, UX-B6, UX-B7, UX-B8, UX-C9, UX-C12.
12. **Smaller waiter/customer items** — UX-W11, UX-W12, UX-W13, UX-W16, UX-W17, UX-W19, UX-W20,
    UX-W21, UX-Q6 (Q6), UX-Q7 (Q7), UX-Q8, UX-Q12, UX-Q13, UX-Q14 (Q8).

Deferred by default, recorded rather than dropped: UX-B9 (Order Points density — a story of its
own), UX-Q11 (customer menu search — a feature, the BA's call), UX-Q15 (legal label language).
