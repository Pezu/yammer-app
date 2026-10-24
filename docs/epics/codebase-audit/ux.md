# UX/UI audit — epic #1

Owner: `ux`. Scope: the four Yammer surfaces as they exist on `origin/develop` (`628c193`).

> **Second-pass addendum pending.** A second pass over `index.html`,
> `public/manifest.webmanifest` and `shared/tz.ts` produced a further ~24 findings
> (`UX-C13`–`UX-C18`, `UX-B10`–`UX-B16`, `UX-W22`–`UX-W25`, `UX-S7`–`UX-S8`, `UX-Q16`–`UX-Q20`).
> **Those finding bodies are not in this file yet** — only their summary line arrived, so the
> register below is the first pass plus the one second-pass item I verified myself (folded into
> `UX-C8`). Counts below describe what is actually written here. The addendum lands as a follow-up
> commit on this branch; the full second-pass text is preserved at
> `.worktrees/ux-parallel-session-audit.md` (gitignored) so nothing is lost. Existing IDs will not
> move when it lands.

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

**Counts (what is written below):** 63 findings — 0 critical, 29 major, 29 minor, 5 trivial.
Per surface: cross-cutting 12 (9 major), backoffice 9 (0 major), waiter 21 (9 major), service
board 6 (4 major), customer 15 (7 major). With the pending addendum the register reaches ~87.

Nothing is graded critical: every finding has a workaround, even if an ugly one. The three that
come closest — a failed payment wiping the bill (`UX-C3`), the picker swallowing "this table still
owes money" (`UX-W1`), and a guest ordering with no cart to review (`UX-Q1`) — are the ones to fix
first.

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

### UX-C8 — One tenant's identity is baked into the shipped app · **major** (tenancy)

Three places, all verified:

- `shared/site-footer.component.ts`: `© RENDEZVOUS EVENTS S.R.L. — CUI 41973877`, on every page of
  a multi-tenant product. A guest scanning a QR code at a *Glo* table is told they are dealing
  with Rendezvous Events, so the ANPC/Netopia merchant identification is wrong for every tenant
  but one.
- `web/public/manifest.webmanifest`: `"name": "RENDEZVOUS"`, `"short_name": "RENDEZVOUS"`,
  `"description": "Rendezvous — socialize & dance in the citadel."`, and
  `"start_url": "/waiter"`. A guest who uses "Add to home screen" from any tenant's QR page
  installs an app called **RENDEZVOUS** that opens the **waiter** app, not their menu.
  `"theme_color": "#0e1320"` is also off-token (UX-C2).
- `web/src/index.html:14`: `<meta name="apple-mobile-web-app-title" content="RENDEZVOUS">` — the
  same leak on iOS.

The footer needs client legal data from the API — **architects, Q1**. The manifest and the meta tag
do not: they are shipped static files and cannot be per-tenant without server-side templating, so
the spec is to make them **neutral** (`name`/`short_name` → `Yammer`, description → the product
one, `start_url` → `/login`, the iOS title → `Yammer`) and let the per-tenant identity live where
it already can: the client logo and name the customer page loads at runtime.

*(The manifest/`index.html` half of this finding came from the second-pass register; I verified it
against the files before folding it in.)*

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


### UX-C13 — The PWA hard-codes one tenant and sends guests to the staff login · **major** (tenancy)

`public/manifest.webmanifest`: `"name": "RENDEZVOUS"`, `"short_name": "RENDEZVOUS"`,
`"description": "Rendezvous — socialize & dance in the citadel."`, `"start_url": "/waiter"`.
`index.html:15`: `<meta name="apple-mobile-web-app-title" content="RENDEZVOUS">`. Same root cause
as UX-C8, different surface: a guest at a *Glo* table who uses "Add to Home Screen" on the QR page
— which is exactly what a `display: standalone` manifest invites — gets an icon labelled
RENDEZVOUS that opens **`/waiter`**, the staff login, not the menu they scanned.

**Spec.** `name`/`short_name` → `Yammer`, `description` → a product line with no venue in it,
`start_url` → `/`, and drop the Apple title override (the manifest's `short_name` covers it).
A genuinely per-tenant manifest (served per client, with the venue's name and icon) is a larger
change and a separate story — recorded, not specced here.

### UX-C14 — `<html lang>` is always `en` · **minor** (a11y)

`index.html:2`. Both bilingual surfaces default to Romanian (`I18nService.defaultFor`), so for the
majority of sessions the document declares a language it is not using: screen readers pronounce
Romanian with an English voice, and browsers offer to "translate this page from English".

**Spec.** `I18nService.setLang()` and `init()` also set `document.documentElement.lang`. The
backoffice, login and service board set `en` explicitly, so a waiter switching surfaces does not
leave `ro` behind.

### UX-C15 — A translucent status bar is declared but no surface reserves the safe area · **major**

`index.html:13` sets `apple-mobile-web-app-status-bar-style: black-translucent` and the manifest
asks for `display: standalone`, which together put the web view **under** the iPhone status bar.
The viewport meta (`:6`) has no `viewport-fit=cover`, and `env(safe-area-inset-*)` appears in
exactly two places in the whole app (`waiter-table-detail-page.scss:46`,
`site-footer.component.ts:49`). Installed on a phone, the waiter top bar (logo, approvals badge,
hamburger) and the customer's sticky top bar sit under the clock and the notch, and the customer
cart bar, both pay sheets and the assign picker sit under the home indicator.

**Spec.** `viewport-fit=cover` in the viewport meta, then:
`padding-top: env(safe-area-inset-top)` on `.topbar` in `waiter-page.ts` and
`customer-order-point-page.ts` (height grows by the inset, not the content);
`padding-bottom: calc(<existing> + env(safe-area-inset-bottom))` on `.cart-bar` (both order
pages), `.pay-sheet`, `.picker` and the service board's `.board`. Nothing changes in a browser
tab, where every inset is 0.

### UX-C16 — Login fields are labelled only by their placeholder · **minor** (a11y)

`login.html:14-36`: the two inputs have `placeholder="Username"` / `"Password"` and no `<label>`,
no `id`/`for`, no `aria-label`; the validation messages (`:19-21`, `:30-32`) are not linked with
`aria-describedby` and the invalid input gets a red border but no `aria-invalid`. The placeholder
also disappears as soon as the field has content, so a user who looks away loses the label. This is
the gate to every surface, and the only screen a waiter sees before their shift.

**Spec.** Visually-hidden `<label for>` for each field, `[attr.aria-invalid]` and
`[attr.aria-describedby]` → the message id when invalid, and keep the placeholders as hints.
A show/hide password toggle (44×44, `aria-pressed`) is worth adding on a surface used one-handed in
a dark venue — recorded as part of the same story.

### UX-C17 — The NETOPIA mark is loaded from a third-party origin · **minor**

`site-footer.component.ts:28`: `<img src="https://mny.ro/np-black-0.svg">`, on every page of every
surface. If that host is slow, down or blocked, the payment mark the Netopia account requires
simply is not there; it also leaks every guest's IP to a third party and has no `width`/`height`,
so the footer shifts as it loads.

**Spec.** Self-host the SVG under `web/public/assets/images/`, with explicit `width`/`height`.
Same asset, same look, no third-party request.

### UX-C18 — No `prefers-reduced-motion` anywhere · **trivial**

`confirm-dialog` (`fade`, `pop`), `payment-return-page` (`ret-spin`), the customer approval
spinner, the sidebar's `max-height` transition and the `.icon-btn` hover `translateY` all animate
unconditionally.

**Spec.** One global block in `styles.scss`:
`@media (prefers-reduced-motion: reduce) { *, *::before, *::after { animation-duration: 0.01ms !important; animation-iteration-count: 1 !important; transition-duration: 0.01ms !important; } }`,
with the two spinners kept (they communicate progress) by excluding them from the rule.

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

### UX-B5 — Collapsed groups keep their links in the tab order and expose no state · **major** (a11y)

Two problems in the same place. `backoffice-layout.scss:86-95` collapses a group with
`overflow: hidden; max-height: 0` — the links are *clipped*, not removed, so they stay focusable:
tabbing through the sidebar moves focus into invisible links and the browser scrolls a clipped
container. And the four `.group-toggle` buttons (`backoffice-layout.html:14, 59, 92, 125`) carry no
`aria-expanded`/`aria-controls`, while the parent group of the active page gets no active styling
(only the leaf `routerLinkActive`).

Re-graded from minor to **major** on the second pass: focus going somewhere invisible is a
2.4.3/2.4.7 failure, not polish.

**Spec.** `visibility: hidden` on the collapsed state with
`transition: max-height 0.2s ease-in-out, visibility 0s 0.2s` (and `visibility: visible` +
`transition: max-height 0.2s ease-in-out` when open), so the links leave the tab order the moment
the group closes; `[attr.aria-expanded]="configOpen()"` + `aria-controls` on each toggle;
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


### UX-B10 — Two reports show timestamps one to three hours early · **major**

`shared/tz.ts` is the documented single source of truth for display: the api returns naive
wall-clock strings recorded in UTC, so they must be parsed as UTC and rendered in
`Europe/Bucharest`. `shifts-page` and `dashboard-page` use its `roDate` pipe and are correct.
`not-paid-report-page.html:76` and `open-tables-report-page.html:55` use Angular's
`| date: 'dd.MM.yyyy HH:mm'`, which parses a zone-less string as **local** time — so those two
reports render every timestamp one hour (winter) or three hours (summer) earlier than the same
event on the dashboard and the shifts page.

Who it hurts: a manager reconciling the Not-paid report or the open tables against the service day
reads two different clocks for the same payment and cannot tell which is right.

**Spec.** `{{ row.at | roDate }}` and `{{ row.openedAt | roDate }}` (import `RoDatePipe`), dropping
`DatePipe` from both components. Also a defect — raised with the architects so it is fixed once,
not twice.

### UX-B11 — The Open tables report never refreshes · **major**

`open-tables-report-page.ts:72-88` loads once per location change: no poll, no refresh button, no
"updated at". It is the live "which tables still owe money right now" view, used at closing time
next to the Shifts page — which does re-check its blockers every 15 s. A manager reading a frozen
list believes a table is settled when it is not.

**Spec.** A refresh button in `.header-actions` (the `.add-btn` shape, a refresh glyph,
`aria-label="Refresh"`) plus `Updated HH:mm:ss` in `--muted` beside it, and a 30 s poll while the
document is visible, replacing rows in place. Same treatment for the Shifts blockers already
present, so the two pages agree.

### UX-B12 — The Menu page's colour picker lets an admin make the guest menu unreadable · **major** (a11y)

`menu-page.html:286`: `<input type="color" class="rt-color" value="#283c50">` writes an inline
colour into a category name, which is then rendered with `[innerHTML]` on the customer page, the
waiter order page and inside combo options. An admin can pick white on the white customer
background, or any low-contrast pair, and nothing warns them — an accessibility failure introduced
through content, outside the token system, on the public surface.

**Spec.** Remove the free picker. If emphasis is genuinely wanted, offer four swatches bound to
tokens (`--text`, `--muted`, `--primary`, `--danger`), all of which are known-contrast against
`--surface`. Part of the same consolidation as UX-C11's note about this toolbar duplicating
`rich-text-editor`.

### UX-B13 — A bare `.icon-btn` turns white on white on hover · **minor**

`_crud-table.scss:206-212`: `.icon-btn:hover { color: #fff; transform: … }`, while only the
`.edit` / `.save` / `.danger` / `.cancel` modifiers set a background. Any `.icon-btn` used without
a modifier — `service-page.html:14, 22, 34` reuses the class name on the board's top bar — becomes
an invisible white glyph on a white surface on hover.

**Spec.** Move `color: #fff` out of the base `:hover` and into each modifier, so a bare
`.icon-btn` only gets its `--page-bg` background on hover.

### UX-B14 — Money columns in the reports carry no unit · **minor**

`open-tables-report-page.html:56` (Amount), `payments-report-page` (Amount / Tip / Total),
`not-paid-report-page`, `orders-report-page` and the dashboard's per-waiter table
(`dashboard-page.html:230-240`) all print bare numbers, while the dashboard's KPI tiles and every
waiter screen print `RON`. Three of those reports are printed or exported and read away from the
screen.

**Spec.** The unit goes in the column header — `Amount (RON)`, `Tip (RON)`, `Total (RON)` — once
per report, not in every cell. Pairs with UX-C1: the backoffice keeps `en-US` grouping, so the
header is the only thing that changes.

### UX-B15 — `open-tables-report-page` borrows another page's stylesheet · **trivial**

`open-tables-report-page.ts:14`: `styleUrl: './payments-report-page.scss'`. It is also the only
report whose component does not `@use` `crud-table` itself. It works, but the Payments report can
no longer be restyled without silently restyling Open tables.

**Spec.** Its own `open-tables-report-page.scss` that `@use`s `crud-table` and holds the `.num` /
`.totals` rules it actually needs.

### UX-B16 — Table headers carry no `scope` · **trivial**

Every `.table` in the backoffice. Implicit scope covers the simple single-row cases; the
dashboard's and the Not-paid report's grouped tables are where a screen reader loses the thread.

**Spec.** `scope="col"` on every `thead th`, `scope="row"` on the Not-paid report's group rows.
One attribute per header; no visual change.

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

A tile with nothing due and nothing pending looks exactly as it does today.

**API — answered (architect-b, Q2): yes, additive.** All four fields come off the current schema:
`unpaidTotal` is the open-tables report's computation per point, `readyOrderCount` is
`status = READY` per point, `pendingApprovalCount` is PENDING `customer_sessions` + DRAFT orders
(the approvals query already exists), and `roundOpenedAt` is the earliest active assignment's
`assigned_at` — which *is* the round start by definition (`TableRoundService`). Architect-b's
contract condition, which this spec adopts: **one response, 3–4 grouped/batched queries, never a
per-point lookup** — the tiles are a hot path polled every 15 s and must not become an N+1.

### UX-W3 — The board never refreshes · **major**

`waiter-tables-page.ts` loads once in the constructor. Nothing polls, nothing subscribes to
`/api/ws/orders` (the service board does). A split created by a colleague, a new customer order,
a payment taken on a shared table — none of it appears until the waiter leaves and returns, or
uses the top-bar refresh, which is a `window.location.reload()` (`waiter-page.ts:refreshPage`).
With UX-W2 the staleness becomes actively misleading: a tile would show an amount that is already
paid.

**Spec.** Refresh `loadAssigned()` on a 15 s interval (aligned with the approvals poll already in
the shell) and on `visibilitychange` → visible, replacing tiles in place with no loading flash —
the picker already does exactly this (`loadBoard()`).

**Polling, not push — settled (architect-b, Q2).** `OrderNotificationService` routes live push by
**SERVICE-station assignment**, so the socket today reaches service-board users only; it has no
concept of the waiters assigned to a table. Pushing to waiters is therefore a new api story, not a
detail of this spec. The 15 s poll is the whole fix. Waiter WebSocket push is recorded as a
deferred improvement (§10) rather than filed: a 15 s staleness window on a board the waiter is
holding in their hand is acceptable, and the same poll carries UX-W10.

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

### UX-W14 — A typed amount over the bill is clamped on screen but sent unclamped · **major**

In AMOUNT mode the input accepts `500` on a 50 RON bill. `grossTotal()` clamps for *display* —
`round2(Math.min(this.fixedAmount() ?? 0, due))`, `waiter-table-detail-page.ts:238` — so the
summary rows read `Amount 50.00 / Total to pay 50.00`. But the request sends the raw field:
`payload.amount = this.fixedAmount() ?? 0` (`:563`). The waiter charges from a screen that says
50.00 while 500 goes to the server.

Re-graded from minor to **major** on the second pass: the display and the submitted amount are
different numbers, which is a money-reading failure whatever the api then does with it. Flagged to
the architects as well (distinct from `#5`, which is about discounts — and note the UI already
hides AMOUNT mode when the table has a discount, `waiter-table-detail-page.html:93`, which bounds
that issue's reach).

**Spec.** `[max]` on the input; when the typed value exceeds what is due, a hint line under it
(`t('pay.amountCapped', { max })` — "Maximum due is {max} RON") in `--danger` and the method
buttons disabled, so nothing can be submitted in the divergent state. Either the submitted value
is clamped to match the display, or the UI refuses — it must not be possible for the two to
differ.

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


### UX-W22 — The Pay button vanishes when the point has no payment type · **major**

`waiter-table-detail-page.ts:75-77`: `canPay = unpaidTotal > 0 && payOptions().length > 0`, and
the footer renders Pay only `@if (canPay())`. On an order point with no payment types configured,
the waiter sees `TOTAL DUE 120.00 RON` and **no Pay button and no reason** — the action simply is
not there. The order page handles the identical situation properly and says so
(`order.noPaymentTypes` — "No payment type is set for this order point — ask an admin",
`waiter-order-page.ts:360`).

**Spec.** `canPay` drops the `payOptions()` condition, so Pay appears whenever money is due. When
`payOptions()` is empty the sheet opens and renders `t('pay.noTypes')` in the `.methods` slot
instead of buttons (copy in §8). The waiter learns what is wrong and who fixes it, on the screen
where they noticed.

### UX-W23 — The money screens never name the service day they report on · **minor**

Payments and Statistics both scope to the location's open service day — the right POS semantics,
and no date picker, per `CLAUDE.md` §5 — but neither screen says *which* day. `stats.empty` even
reads "Nothing collected on **this day**" without naming it. A waiter cashing up cannot confirm the
numbers are tonight's, and after a manager closes the day and opens the next one the same screen
silently changes meaning.

**Spec.** A subheader on both pages, left of the existing filter combo, `0.8rem` `--muted`:
`t('tables.serviceDay', { name })` → "Service day · Fri 24.10, 17:00" /
"Ziua de serviciu · Vi 24.10, 17:00", from the open shift's nickname when it has one and its start
time otherwise. Needs the open shift readable by a waiter — **architects, Q12.**

### UX-W24 — One in-flight decision disables every button on the Approvals page · **minor**

`waiter-approvals-page.ts:223`: a single `busy` boolean gates every Approve and Deny button
(`:40-41, 68-69`). With four guests waiting, approving one greys out the whole queue at
`opacity: 0.6` until the call returns. The Orders page already does this per id
(`busy = signal<Set<string>>`).

**Spec.** `busy` becomes a `Set<string>` keyed by the customer/order id, as on the Orders page;
only the acted-on card's two buttons disable, and that card shows the pending state.

### UX-W25 — The top-bar refresh reloads the whole app · **major**

`waiter-page.ts:303-306`: `refreshPage()` = `menuCache.clear()` + `window.location.reload()`, on a
36×36 button next to the hamburger on **every** waiter screen, with no confirmation. Two
consequences: a half-built order on the order page is destroyed (UX-W16 — the waiter cart is not
persisted), and on the venue wifi a reload can fail to come back at all, leaving the waiter on a
browser error page mid-service. The hamburger already has the safe version ("Refresh menu", which
only drops the cache).

**Spec.** The top-bar button re-fetches the current page's data instead of reloading the document —
every waiter page is signal-driven, so a `refresh()` output on the shell that the active child
subscribes to is enough (and UX-W3/UX-W10's polling makes it rarely needed). If a document reload
is kept for the cache, it goes through `confirm-dialog` whenever the order page has a non-empty
cart.

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
and re-inserts the card.

**Undo, not a confirm — settled (architect-b, Q4).** `DELIVERED → READY` is accepted today, though
only because `OrderService.java:701-716` checks set membership and nothing else (the architects'
own finding A5). Their transition matrix will **explicitly keep** `DELIVERED → READY` as the undo
path, because a kanban status never touches payment state — only `CANCELED` is money-relevant. So
this spec takes the Undo toast and drops the confirm-dialog fallback: a confirm on every Delivered
would tax the common case to protect against the rare mis-tap, which is exactly what an undo is
for. The agreed matrix for reference: `ORDERED ↔ READY`, `READY → DELIVERED`,
`DELIVERED → READY`, `{ORDERED, READY} → CANCELED` only when no line is paid, and nothing leaves
`CANCELED`.

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


### UX-S7 — A SERVICE user whose location has no station cannot log out · **major**

`service-page.html:31-44`: the station picker's close button is rendered only `@if (station())`,
which is right for "you must pick one before you see the board" — but when
`stations().length === 0` the picker shows "No service stations in your location yet — ask an admin
to add one." over an overlay that also covers the top bar, where Logout lives. There is no close
button (no station), no Logout inside the picker, and no route away. The only exit is clearing site
data.

**Spec.** The picker head always renders a trailing button: the X when a station is held, a
`Logout` text button when none is. In the empty state, Logout becomes the primary action under the
message, so the dead end is also the explanation.

### UX-S8 — Nothing says when the board has gone stale · **major**

`service-page.ts:187-227` is solid plumbing — WebSocket push, 3 s auto-reconnect, polling as the
safety net — with no indicator. When the socket closes and the tablet's wifi is down, the board
keeps showing the orders it last saw; a frozen board and a quiet kitchen look identical, on a
screen nobody is standing in front of. UX-S3 fixes "blank vs broken" for the first load; this is
the same problem after an hour of service.

**Spec.** A live pill in the top bar, left of the user name, `role="status" aria-live="polite"`,
at the board's type scale (UX-S1): green dot + `Live · updated 21:04:12` while
`ws?.readyState === WebSocket.OPEN`, `--warn` dot + `Reconnecting… · updated 20:58:41` otherwise,
both driven by a `lastLoadedAt` signal set in `load()`'s `next`. After 60 s disconnected, the
board header strip also tints `--warn-soft` — a kitchen must not have to read text to notice.
No API change: the component already knows both facts.

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

There is a second, compounding problem: `−` is rendered only when `qty > 0`
(`customer-order-point-page.ts:210-213`), so the row re-lays out on the first tap and the `+`
button **shifts left** by the width of `−` plus the value. A guest tapping `+` twice in quick
succession lands the second tap on the quantity or on `−`.

**Spec.** 40×40px, gap `10px`, `-webkit-tap-highlight-color: transparent`, quantity value
`min-width: 24px` at `16px/700`. The `.item-side` column widens accordingly; the item row grows by
~16px, which the 15px type (UX-Q3) absorbs. Reserve the stepper's full width at qty 0 — render the
`−` and the value as `visibility: hidden` placeholders rather than omitting them — so `+` never
moves under the thumb.

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
3. **Change goes through a confirm dialog, because it is not free** (see the answer below):
   `cust.changeTableConfirmTitle` / `cust.changeTableConfirmBody`, confirm `cust.changeTable`,
   cancel `common.cancel`. The body states both consequences in plain words — the guest waits for
   the waiter again, and orders already placed stay on the old part of the table.

**API — answered (architect-b, Q6), and it constrains this spec.** A second join at the sibling
slot is allowed and *is* the mechanism: each slot is its own order point, and `join()` creates a
fresh PENDING session there (`CustomerAccessService.java:43-54` resumes only within the same
point's current round). Two consequences I had hoped to avoid:

- **approval does not carry over** — the guest returns to the "waiting for waiter" banner;
- **orders already placed do not move** — they stay on the wrong slot's bill, and no order-move
  endpoint exists; the waiter resolves it at the table.

So "Change" is a recovery path with a real cost, not an undo, and the copy has to say so rather
than imply a clean switch. The clean fix — moving orders between sibling slots — is **deferred**
(§10): architect-b assesses it as feasible (same location, same table round) but money-path work
needing design care, not a quick addition. It stays recorded with that reason. One consequence for
story trimming: the *prevention* in point 2 now carries more weight than the cure, so the "not
sure, ask your waiter" line is not decoration and should survive any scope cut.

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

**Spec.** Under the heading: `ret.paidAmount` ("Paid {amount} RON") and the order number as
`ret.paidOrder` ("Order #{no}") — both unconditional. Plus a `cust.order` link to the bill view
beside `ret.back`.

**API — answered (architect-b, Q8): yes, trivial.** The response is `{status, orderId}` today
(`OnlinePaymentStatusResponse`); the intent row already stores the amount and the confirmed order
already has its `orderNo`, so this is a two-field DTO addition. Nothing in the spec is conditional
any more.

### UX-Q15 — Legal links are Romanian-only on the English page · **trivial**

`LEGAL_LINKS` labels are fixed Romanian strings in both the footer and the customer drawer. The
documents themselves are Romanian legal texts, so the labels are arguably correct; recorded so the
decision is on the record rather than accidental. If translated, EN labels go in the same
`LEGAL_LINKS` structure keyed by language.


### UX-Q16 — After ordering in CONFIRM mode the bill says "No orders yet" · **major**

In `CONFIRM` self-order mode a customer order is created as a DRAFT on the waiter's Approvals page
and carries no number until it is approved, so it is not in the bill. The guest is shown
`cust.orderSentApproval` ("Your order was sent to the waiter for approval."), opens drawer → Order,
and reads `cust.noOrders` — "No orders yet." Two screens of the same app contradict each other
about whether the order exists, immediately after the guest acted.

**Spec.** A notice at the top of the Order view, in the `approval-note` style already on the page,
while this device has an order awaiting approval: `t('cust.pendingApprovalLine')` ("You have an
order waiting for the waiter to approve it." / "Ai o comandă în așteptarea aprobării
ospătarului."). Needs the bill payload to report a pending-approval count for this token —
**architects, Q11.** Pairs with UX-Q8 (the bill does not refresh either), so the notice clears
itself once the order is approved and appears as a normal line.

### UX-Q17 — The cart bar vanishes when ordering becomes impossible · **major**

`customer-order-point-page.ts:150`: the bar renders only
`@if (view() === 'menu' && cartCount() > 0 && canOrder())`. If the waiter denies the device, or
closes the table, while the guest is building a cart, the bar — and with it the total and the only
way to send the order — **disappears with the items still in the cart** and nothing explains why.
The guest keeps tapping `+` on a page that no longer has an order button.

**Spec.** Keep the bar whenever `cartCount() > 0`; when `canOrder()` is false, the button is
disabled and its label is replaced by the reason: `t('cust.cannotOrderNow')` ("Ordering is paused
at this table." / "Comanda este oprită la această masă."), with the existing DENIED/not-open banner
above giving the detail and UX-Q7's "Ask again" the way out.

### UX-Q18 — "Waiting for the waiter" can spin forever · **minor**

`customer-order-point-page.ts:1044-1058`: a 5 s poll with no cap, behind a spinner and
`cust.waitingApproval` ("Waiting for the waiter to approve this table…"). If nobody opens the
Approvals page, the guest watches that spinner indefinitely and is never told to do anything else.

**Spec.** Keep polling, change the copy after 2 minutes: `t('cust.waitingLong')` ("The waiter has
not answered yet — please call them over." / "Ospătarul nu a răspuns încă — te rugăm să îl
chemi."), spinner retained so it still reads as in progress.

### UX-Q19 — The cart-bar total carries no currency · **minor**

`customer-order-point-page.ts:154`: `{{ price(cartTotal()) }}` — a bare number, next to a count of
items, while every item price (`:206`) and the bill total (`:119`) print `RON`. The one number the
guest checks before committing is the only money on the page without a unit.

**Spec.** `{{ price(cartTotal()) }} RON`, matching the bill total. The "drop a pointless `.00`"
rule stays as it is.

### UX-Q20 — Quick-nav scrolls the category heading under the sticky top bar · **minor**

`customer-order-point-page.ts:1076-1078`: `scrollIntoView({ behavior: 'smooth', block: 'start' })`
against a 56 px sticky top bar (`:258-262`), so the category the guest tapped ends up hidden behind
the bar and the first thing they see is the second item of that category.

**Spec.** `scroll-margin-top: 64px` on `.menu-cat` (56 px bar + 8 px breathing room); no JS change.

---

## 7. Questions for the architects

Each of these gates a state specced above; `ux` needs the answer before the matching story goes
`stage:ready`. **Q2, Q4, Q6 and Q8 are answered** (architect-b, 2026-10-24) and the specs above
have been rewritten accordingly — the answers are recorded in the finding, not here, so the spec
and the contract can't drift apart. Q1, Q3, Q5, Q7 and Q9 are open with architect-a.

| # | For | Question |
|---|---|---|
| Q1 | UX-C8, UX-Q9 | Can the public order-point payload carry the client's **display name and legal line** (company, CUI), so the footer and the logo `alt` are per tenant instead of hard-coded Rendezvous? |
| ~~Q2~~ | UX-W2 | **Answered: yes, additive.** All four fields derive from the current schema; one response, 3–4 batched queries, no per-point lookup. |
| Q3 | UX-W10 | Is there (or can there be) a cheap count for "READY orders at my tables", or should the shell poll the existing list? Alternatively, should the waiter shell subscribe to `/api/ws/orders`? |
| ~~Q4~~ | UX-S4 | **Answered: yes, and the coming transition matrix keeps it** as the undo path (kanban status never touches payment state). Undo toast, no confirm dialog. |
| Q5 | UX-Q2 | Does the public order-point payload say whether the point self-pays `ONLINE` (and with what total), so the button can read "Pay X RON" before the redirect? |
| ~~Q6~~ | UX-Q6 | **Answered: a second join is allowed and is the mechanism**, but approval does not carry over and placed orders do not move. Spec now warns about both; moving orders is deferred. |
| Q7 | UX-Q7 | May a DENIED device re-request access (new PENDING), or is Deny terminal for that device/token? |
| ~~Q8~~ | UX-Q14 | **Answered: yes, trivial** — a two-field DTO addition; the intent row already stores the amount. |
| Q9 | UX-W18 | Does the pay response expose the fiscal status, so the toast can distinguish "receipt printing" from "receipt issued"? |

Token change for UX-C2 and the `--focus-ring` in UX-C4 also needs an architect decision — as **one**
decision in `design.md`, not re-argued per story.

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
| `cust.changeTableConfirmTitle` | `Change your table?` | `Schimbi masa?` |
| `cust.changeTableConfirmBody` | `Orders you already placed stay on {name}, and the waiter has to approve you again.` | `Comenzile deja trimise rămân pe masa {name}, iar ospătarul va trebui să te aprobe din nou.` |
| `cust.askAgain` | `Ask again` | `Cere din nou` |
| `cust.search` | `Search the menu…` | `Caută în meniu…` |
| `ret.checkingText` *(replaces)* | `This usually takes a few seconds. Please keep this page open.` | `Durează de obicei câteva secunde. Te rugăm să nu închizi pagina.` |
| `ret.paidAmount` | `Paid {amount} RON` | `Plătit {amount} RON` |
| `ret.paidOrder` | `Order #{no}` | `Comanda #{no}` |
| `nf.title` | `This code is not working` | `Codul nu funcționează` |
| `nf.body` | `Ask your waiter for a menu, or scan the code again.` | `Cere ospătarului meniul sau scanează din nou codul.` |
| `pay.noTypes` *(UX-W22)* | `No payment type is set for this table — ask an admin.` | `Nicio metodă de plată setată pentru această masă — cere unui admin.` |
| `payments.pendingNote` *(UX-W7)* | `Fiscal receipt not issued yet — it is printing.` | `Bonul fiscal nu a fost emis încă — se tipărește.` |
| `tables.serviceDay` *(UX-W23)* | `Service day · {name}` | `Ziua de serviciu · {name}` |
| `cust.pendingApprovalLine` *(UX-Q16)* | `You have an order waiting for the waiter to approve it.` | `Ai o comandă în așteptarea aprobării ospătarului.` |
| `cust.cannotOrderNow` *(UX-Q17)* | `Ordering is paused at this table.` | `Comanda este oprită la această masă.` |
| `cust.waitingLong` *(UX-Q18)* | `The waiter has not answered yet — please call them over.` | `Ospătarul nu a răspuns încă — te rugăm să îl chemi.` |
| `common.refresh` *(UX-W25)* | `Refresh` | `Reîncarcă` |

Existing entries to correct:

| key | problem | fix |
|---|---|---|
| `stats.comped` | EN `Comped` (jargon), RO identical to `stats.protocol` | EN → `Protocol / PO`, RO stays `Protocol / PO` |
| `pay.tip` / `payments.tip` / `stats.tip` | RO alternates `Bacșiș` and `Tips` for the same thing | all three RO → `Bacșiș` |
| `pay.customPct` / `pay.percent` | both RO `Procent` for two different controls | `pay.customPct` RO → `Procent %`, `pay.percent` RO → `Procent` |
| `detail.proforma` | EN `Proforma` (noun) vs RO `Printează nota` (verb) | EN → `Print bill`, RO stays |
| `tables.assignedMany` | RO `{n} alocați` — masculine, for feminine `mese` | see the note below: EN and RO are both ambiguous about *what* is counted |
| `cust.denied` | terminal — says what happened, never what to do next (UX-Q7) | EN → `The waiter declined access at this table — please order with your waiter.`, RO → `Ospătarul a refuzat accesul la această masă — comandă, te rog, prin ospătar.` |

Two copy decisions for `ba`, recorded rather than guessed:

- **`tables.assignedOne` / `tables.assignedMany`.** EN is "1 assigned" / "{n} assigned" and RO is
  "1 alocat" / "{n} alocați". On a multi-user point the number counts **waiters**, not tables, so
  the RO masculine is arguably right and the fix is to make both languages say what is counted:
  EN `{n} waiters` / RO `{n} ospătari`. If the number is meant to read as "this point is assigned,
  N times", then `{n} alocate` (agreeing with `mese`) is the fix instead. One of the two, not both.
- **Romanian plural with "de".** From 20 up, Romanian needs *de*: `20 de produse`, `20 de ore`,
  `20 de ospătari`. `I18nService.t` has no plural support, so today `cust.itemMany`,
  `time.hoursAgo`, `time.minAgo` and `tables.assignedMany` are wrong for any count ≥ 20 — easy to
  hit with items on a table of eight. Cheapest fix without a plural engine: a second key per case
  (`…Many` / `…ManyDe`) chosen at the call site on `n >= 20`. A real plural rule in `t()` is the
  better long-term answer and is a story of its own.

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

Deferred by default, recorded rather than dropped:

- **UX-B9** — Order Points density (13 editable columns); a story of its own.
- **UX-Q11** — customer menu search; a feature, the BA's call.
- **UX-Q15** — legal label language on the EN customer page.
- **Waiter WebSocket push** (from Q2/UX-W3) — `OrderNotificationService` routes by SERVICE-station
  assignment and has no notion of a table's waiters, so pushing to waiters is a new api story. The
  15 s poll makes it unnecessary for now; recorded so the option is on the record rather than
  forgotten.
- **Moving orders between sibling split slots** (from Q6/UX-Q6) — the clean fix for a guest who
  picked the wrong half of a table. Architect-b assesses it as feasible (same location, same table
  round) but money-path work needing design care. Until it exists, UX-Q6 is a warned recovery
  path, not an undo, and the waiter resolves mis-slotted orders at the table.
