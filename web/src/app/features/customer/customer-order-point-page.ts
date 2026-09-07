import { Component, OnDestroy, computed, effect, inject, signal } from '@angular/core';
import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import {
  CustomerOrderPoint,
  CustomerOrderPointService,
  CustomerStatus,
  MenuNode,
} from './customer-order-point.service';
import { TransparentImageDirective } from '../../shared/transparent-image.directive';
import { LEGAL_LINKS, SiteFooter } from '../../shared/site-footer.component';

/**
 * Ordering page a customer reaches by scanning an order point's QR code
 * (`/customer/order-point/:id`, public — no login). Ported from old yammer: client
 * logo top-left, hamburger drawer top-right (Menu first), category quick-nav, the
 * table's menu with a cart and Place order. The menu is always browsable; placing
 * an order requires the table's session to be OPEN (else the customer is told to
 * ask a waiter).
 */
@Component({
  selector: 'app-customer-order-point-page',
  imports: [DecimalPipe, NgTemplateOutlet, TransparentImageDirective, RouterLink, SiteFooter],
  template: `
    <header class="topbar">
      <div class="brand">
        <img class="logo" [src]="logoSrc()" alt="logo" (error)="logoFailed.set(true)" />
      </div>
      @if (op(); as o) {
        <span class="table-no">{{ o.name }}</span>
      }
      <button type="button" class="hamburger" (click)="toggleMenu()" aria-label="Menu" aria-haspopup="true">
        <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="3" y1="6" x2="21" y2="6"></line><line x1="3" y1="12" x2="21" y2="12"></line><line x1="3" y1="18" x2="21" y2="18"></line></svg>
      </button>
    </header>

    @if (menuOpen()) {
      <div class="drawer-backdrop" (click)="closeMenu()"></div>
      <nav class="drawer">
        <button type="button" class="drawer-close" (click)="closeMenu()" aria-label="Close menu">
          <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
        </button>
        <button type="button" class="drawer-item active" (click)="closeMenu()">Menu</button>
        <div class="drawer-legal">
          @for (link of legalLinks; track link.slug) {
            <a class="drawer-legal-item" [routerLink]="['/legal', link.slug]" (click)="closeMenu()">{{ link.label }}</a>
          }
        </div>
      </nav>
    }

    <main class="cust">
      @if (loading()) {
        <p class="state">Loading…</p>
      } @else if (error()) {
        <p class="state err">{{ error() }}</p>
      } @else if (op(); as o) {
        @if (placed()) {
          <div class="placed" role="status">
            {{ o.selfOrderMode === 'CONFIRM' ? 'Your order was sent to the waiter for approval.' : 'Your order has been sent. Thank you!' }}
          </div>
        }
        @if (orderError()) {
          <div class="order-err" role="alert">{{ orderError() }}</div>
        }
        @if (o.sessionOpen && o.selfOrderMode !== 'DISALLOW') {
          @if (customerStatus() === 'PENDING') {
            <div class="approval-note" role="status">
              <span class="spinner"></span>
              Waiting for the waiter to approve this table…
            </div>
          } @else if (customerStatus() === 'DENIED') {
            <div class="order-err" role="alert">The waiter declined access at this table.</div>
          }
        }

        @if (o.menu.length > 0) {
          @if (topCategories().length > 1) {
            <nav class="cat-nav">
              @for (cat of topCategories(); track cat.id) {
                <button type="button" class="cat-chip" (click)="scrollTo(cat.id)" [innerHTML]="cat.name"></button>
              }
            </nav>
          }
          <div class="menu-tree">
            @for (node of o.menu; track node.id) {
              <ng-container *ngTemplateOutlet="nodeTpl; context: { $implicit: node, level: 0 }"></ng-container>
            }
          </div>
        } @else {
          <p class="soon">No menu available for this table yet.</p>
        }
      }

      @if (cartCount() > 0 && canOrder()) {
        <footer class="cart-bar">
          <div class="cart-info">
            <span class="cart-count">{{ cartCount() }} item{{ cartCount() === 1 ? '' : 's' }}</span>
            <span class="cart-total">{{ cartTotal() | number: '1.2-2' }}</span>
          </div>
          <button type="button" class="order-btn" [disabled]="placing()" (click)="placeOrder()">
            {{ placing() ? 'Sending…' : 'Place order' }}
          </button>
        </footer>
      }
    </main>

    @if (op()) {
      <app-site-footer />
    }

    <!-- Recursive node: an orderable product renders as a row; a category renders a header + its children. -->
    <ng-template #nodeTpl let-node let-level="level">
      @if (node.orderable) {
        <div class="item-card">
          @if (node.imageObject && !transparentImages().has(node.imageObject)) {
            <img
              class="item-img"
              appTransparentCheck
              (transparent)="markTransparent(node.imageObject)"
              [src]="imageUrl(node.imageObject)"
              alt=""
            />
          } @else {
            <div class="item-img placeholder"></div>
          }
          <div class="item-body">
            <span class="item-name" [innerHTML]="node.name"></span>
          </div>
          <div class="item-side">
            @if (node.price != null) {
              <span class="item-price">{{ node.price | number: '1.2-2' }} RON</span>
            }
            @if (canOrder()) {
              <div class="qty">
                @if (qty(node.id) > 0) {
                  <button type="button" class="qty-btn" aria-label="Remove one" (click)="dec(node.id)">−</button>
                  <span class="qty-val">{{ qty(node.id) }}</span>
                }
                <button type="button" class="qty-btn" aria-label="Add one" (click)="inc(node)">+</button>
              </div>
            }
          </div>
        </div>
      } @else {
        <section class="menu-cat" [id]="'cat-' + node.id">
          <div class="cat-head" [class.sub]="level > 0">
            <span class="cat-name" [innerHTML]="node.name"></span>
          </div>
          <div class="cat-children">
            @for (child of node.children; track child.id) {
              <ng-container *ngTemplateOutlet="nodeTpl; context: { $implicit: child, level: level + 1 }"></ng-container>
            }
          </div>
        </section>
      }
    </ng-template>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      min-height: 100vh;
      min-height: 100dvh;
      background: #fff;
    }
    .topbar {
      position: sticky;
      top: 0;
      z-index: 10;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      height: 56px;
      padding: 0 1rem;
      background: #fff;
      border-bottom: 1px solid var(--border);
    }
    .brand {
      display: flex;
      align-items: center;
      min-width: 0;
    }
    .logo {
      width: 44px;
      height: 44px;
      object-fit: contain;
      padding: 4px;
      background: #fff;
      border: 1px solid var(--border);
      border-radius: 12px;
      box-shadow: 0 1px 3px rgba(18, 27, 46, 0.08);
    }
    .table-no {
      flex: 1;
      min-width: 0;
      overflow: hidden;
      text-align: center;
      font-size: 1.1rem;
      font-weight: 800;
      color: var(--text);
      white-space: nowrap;
      text-overflow: ellipsis;
    }
    .hamburger {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
      color: var(--text);
      background: none;
      border: none;
      cursor: pointer;
    }
    .drawer-backdrop {
      position: fixed;
      inset: 0;
      z-index: 20;
      background: rgba(18, 27, 46, 0.35);
    }
    .drawer {
      position: fixed;
      top: 0;
      right: 0;
      bottom: 0;
      z-index: 21;
      display: flex;
      flex-direction: column;
      width: 240px;
      max-width: 80vw;
      padding: 4rem 0.75rem 1rem;
      background: #fff;
      box-shadow: -0.5rem 0 1.5rem rgba(18, 27, 46, 0.15);
      overflow-y: auto;
    }
    .drawer-close {
      position: absolute;
      top: 0.75rem;
      right: 0.75rem;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
      color: var(--text);
      background: none;
      border: none;
      cursor: pointer;
    }
    .drawer-item {
      display: block;
      width: 100%;
      padding: 0.85rem 1rem;
      font: inherit;
      font-size: 1rem;
      font-weight: 600;
      text-align: left;
      color: var(--text);
      background: none;
      border: none;
      border-radius: 8px;
      cursor: pointer;
    }
    .drawer-item:hover,
    .drawer-item.active {
      background: var(--page-bg);
      color: var(--primary);
    }
    .drawer-legal {
      margin-top: auto;
      padding-top: 0.75rem;
      border-top: 1px solid var(--border);
    }
    .drawer-legal-item {
      display: block;
      padding: 0.25rem 1rem;
      font-size: 0.7rem;
      color: var(--muted);
      text-decoration: none;
    }
    .drawer-legal-item:hover {
      color: var(--primary);
    }
    .cust {
      width: 100%;
      max-width: 30rem;
      margin: 0 auto;
      padding: 1rem 1.25rem 5rem;
      text-align: center;
      flex: 1;
    }
    .state {
      margin: 3rem 0;
      color: var(--muted);
    }
    .state.err {
      color: var(--danger);
    }
    .placed {
      margin: 0.75rem 0;
      padding: 0.6rem 0.85rem;
      font-size: 0.9rem;
      color: #1f7a3d;
      background: rgba(40, 167, 69, 0.1);
      border: 1px solid rgba(40, 167, 69, 0.25);
      border-radius: 8px;
    }
    .order-err {
      margin: 0.75rem 0;
      padding: 0.6rem 0.85rem;
      font-size: 0.9rem;
      color: var(--danger);
      background: rgba(220, 53, 69, 0.08);
      border: 1px solid rgba(220, 53, 69, 0.25);
      border-radius: 8px;
    }
    .soon {
      color: var(--muted);
      font-size: 0.9rem;
      text-align: center;
    }
    /* horizontal category quick-nav (jump to section) */
    .cat-nav {
      display: flex;
      gap: 8px;
      overflow-x: auto;
      margin: 0.25rem 0 1rem;
      padding-bottom: 4px;
      scrollbar-width: none;
    }
    .cat-nav::-webkit-scrollbar {
      display: none;
    }
    .cat-chip {
      flex-shrink: 0;
      padding: 7px 14px;
      font: inherit;
      font-size: 13px;
      font-weight: 600;
      color: var(--text);
      white-space: nowrap;
      background: #fff;
      border: 1px solid var(--border);
      border-radius: 999px;
      cursor: pointer;
    }
    /* single scrolling list, products one per row */
    .menu-tree {
      display: flex;
      flex-direction: column;
      gap: 0;
      text-align: left;
    }
    .menu-cat {
      scroll-margin-top: 72px;
    }
    .cat-children {
      display: flex;
      flex-direction: column;
      gap: 0;
    }
    .cat-head {
      padding: 28px 0 8px;
    }
    .cat-head.sub {
      padding-top: 12px;
    }
    .cat-name {
      display: block;
      font-size: 14px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: var(--text);
    }
    .cat-head.sub .cat-name {
      font-size: 12px;
      font-weight: 600;
      text-transform: none;
      letter-spacing: 0;
      color: var(--muted);
    }
    .item-card {
      display: flex;
      align-items: stretch;
      gap: 12px;
      min-height: 92px;
      padding: 14px 2px;
      background: #fff;
      border-bottom: 1px solid var(--border);
    }
    .item-img {
      flex-shrink: 0;
      align-self: center;
      width: 64px;
      height: 64px;
      object-fit: cover;
      border-radius: 8px;
    }
    .item-img.placeholder {
      background: var(--page-bg);
      border: 1px dashed var(--border);
    }
    .item-body {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }
    .item-name {
      font-size: 14px;
      font-weight: 600;
      line-height: 1.25;
      color: var(--text);
    }
    .item-price {
      font-size: 12px;
      font-weight: 700;
      color: var(--muted);
      font-variant-numeric: tabular-nums;
    }
    .item-side {
      flex-shrink: 0;
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 8px;
    }
    .qty {
      margin-top: auto;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .qty-btn {
      width: 24px;
      height: 24px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      font-size: 15px;
      font-weight: 700;
      line-height: 1;
      color: var(--primary);
      background: #fff;
      border: 1px solid var(--border);
      border-radius: 50%;
      cursor: pointer;
    }
    .qty-val {
      min-width: 18px;
      font-size: 14px;
      font-weight: 700;
      text-align: center;
      color: var(--text);
      font-variant-numeric: tabular-nums;
    }
    .cart-bar {
      position: fixed;
      left: 0;
      right: 0;
      bottom: 0;
      max-width: 30rem;
      margin: 0 auto;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      padding: 0.75rem 1.25rem;
      background: #fff;
      border-top: 1px solid var(--border);
      box-shadow: 0 -0.5rem 1rem rgba(18, 27, 46, 0.08);
      z-index: 15;
    }
    .cart-info {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
    }
    .cart-count {
      font-size: 0.78rem;
      color: var(--muted);
    }
    .cart-total {
      font-size: 1.1rem;
      font-weight: 800;
      font-variant-numeric: tabular-nums;
      color: var(--text);
    }
    .order-btn {
      padding: 0.7rem 1.5rem;
      font: inherit;
      font-weight: 700;
      color: var(--primary);
      background: #fff;
      border: 1px solid var(--primary);
      border-radius: 8px;
      cursor: pointer;
    }
    .order-btn:disabled {
      opacity: 0.6;
      cursor: default;
    }
    .approval-note {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      margin: 0.75rem 0;
      padding: 0.6rem 0.85rem;
      font-size: 0.9rem;
      color: #92610a;
      background: rgba(245, 158, 11, 0.1);
      border: 1px solid rgba(245, 158, 11, 0.3);
      border-radius: 8px;
    }
    .spinner {
      width: 14px;
      height: 14px;
      flex: none;
      border: 2px solid rgba(146, 97, 10, 0.3);
      border-top-color: #92610a;
      border-radius: 50%;
      animation: cust-spin 0.9s linear infinite;
    }
    @keyframes cust-spin {
      to {
        transform: rotate(360deg);
      }
    }
  `,
})
export class CustomerOrderPointPage implements OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(CustomerOrderPointService);
  private readonly opId = this.route.snapshot.paramMap.get('id') ?? '';

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly op = signal<CustomerOrderPoint | null>(null);

  // app bar / drawer
  readonly menuOpen = signal(false);
  readonly logoFailed = signal(false);
  readonly legalLinks = LEGAL_LINKS;

  // cart: menu item id -> quantity
  readonly cart = signal<Record<string, number>>({});
  readonly placing = signal(false);
  readonly placed = signal(false);
  readonly orderError = signal<string | null>(null);

  // approval: the browser keeps a per-table token; a re-scan resumes the approved session
  private readonly tokenKey = `yammer.customer.${this.route.snapshot.paramMap.get('id') ?? ''}`;
  readonly customerStatus = signal<CustomerStatus>('NONE');
  private approvalPoll: ReturnType<typeof setInterval> | undefined;

  /** Ordering UI is live only on an open table, self-order allowed, and this device approved. */
  readonly canOrder = computed(() => {
    const o = this.op();
    return (
      !!o && o.sessionOpen && o.selfOrderMode !== 'DISALLOW' && this.customerStatus() === 'APPROVED'
    );
  });

  // top-level categories, surfaced as quick-nav chips that scroll to each section
  readonly topCategories = computed<MenuNode[]>(() =>
    (this.op()?.menu ?? []).filter((n) => !n.orderable),
  );

  // image objects detected to have a transparent background — hidden, placeholder shown instead
  readonly transparentImages = signal<Set<string>>(new Set());
  markTransparent(object: string): void {
    this.transparentImages.update((s) => new Set(s).add(object));
  }

  /** Flat list of orderable products in the menu, for cart totals / price lookup. */
  private readonly products = computed(() => {
    const out: MenuNode[] = [];
    const walk = (nodes: MenuNode[]) => {
      for (const n of nodes) {
        if (n.orderable) out.push(n);
        if (n.children?.length) walk(n.children);
      }
    };
    walk(this.op()?.menu ?? []);
    return out;
  });
  private readonly priceById = computed(() => {
    const m = new Map<string, number>();
    for (const p of this.products()) m.set(p.id, p.price ?? 0);
    return m;
  });

  readonly cartCount = computed(() => Object.values(this.cart()).reduce((s, q) => s + q, 0));
  readonly cartTotal = computed(() => {
    const price = this.priceById();
    return Object.entries(this.cart()).reduce((s, [id, q]) => s + (price.get(id) ?? 0) * q, 0);
  });

  private readonly cartKey = `yammer.cart.${this.opId}`;

  constructor() {
    // The customer page is full-white (no admin gray peeking through on mobile overscroll).
    document.body.style.background = '#fff';

    // Persist the cart per order point so a refresh keeps it.
    const savedCart = sessionStorage.getItem(this.cartKey);
    if (savedCart) {
      try {
        this.cart.set(JSON.parse(savedCart));
      } catch {
        /* ignore corrupt cart */
      }
    }
    effect(() => sessionStorage.setItem(this.cartKey, JSON.stringify(this.cart())));

    if (!this.opId) {
      this.error.set('Invalid link.');
      this.loading.set(false);
      return;
    }
    this.service.getOrderPoint(this.opId, this.storedToken()).subscribe({
      next: (op) => {
        this.op.set(op);
        this.customerStatus.set(op.customerStatus);
        this.loading.set(false);
        this.ensureJoined(op);
      },
      error: () => {
        this.error.set('This table could not be found.');
        this.loading.set(false);
      },
    });
  }

  ngOnDestroy(): void {
    document.body.style.background = '';
    this.stopApprovalPoll();
  }

  private storedToken(): string | null {
    try {
      return localStorage.getItem(this.tokenKey);
    } catch {
      return null;
    }
  }

  private storeToken(token: string): void {
    try {
      localStorage.setItem(this.tokenKey, token);
    } catch {
      /* private mode etc. — approval just won't survive the tab */
    }
  }

  /**
   * First contact with an open, self-order table: join (or resume) its session. An
   * unknown device becomes a PENDING request on the assigned waiter's Approvals page;
   * we poll until the waiter decides.
   */
  private ensureJoined(op: CustomerOrderPoint): void {
    if (!op.sessionOpen || op.selfOrderMode === 'DISALLOW') return;
    if (op.customerStatus === 'NONE') {
      this.service.join(this.opId, this.storedToken()).subscribe({
        next: (res) => {
          this.storeToken(res.token);
          this.customerStatus.set(res.status);
          if (res.status === 'PENDING') this.startApprovalPoll();
        },
        error: () => {
          /* table closed in the meantime — the menu stays browsable */
        },
      });
    } else if (op.customerStatus === 'PENDING') {
      this.startApprovalPoll();
    }
  }

  private startApprovalPoll(): void {
    if (this.approvalPoll) return;
    this.approvalPoll = setInterval(() => {
      this.service.getOrderPoint(this.opId, this.storedToken()).subscribe({
        next: (op) => {
          this.op.set(op);
          this.customerStatus.set(op.customerStatus);
          if (op.customerStatus !== 'PENDING') this.stopApprovalPoll();
        },
        error: () => {
          /* keep polling */
        },
      });
    }, 5000);
  }

  private stopApprovalPoll(): void {
    if (this.approvalPoll) {
      clearInterval(this.approvalPoll);
      this.approvalPoll = undefined;
    }
  }

  imageUrl(object: string): string {
    return this.service.imageUrl(object);
  }

  /** Smooth-scroll the menu to a top-level category section. */
  scrollTo(categoryId: string): void {
    document
      .getElementById('cat-' + categoryId)
      ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  /** Brand logo: the client's logo, falling back to the app placeholder when absent/unloadable. */
  logoSrc(): string {
    const clientId = this.op()?.clientId;
    if (!clientId || this.logoFailed()) return 'assets/images/logo-abbr.png';
    return this.service.clientLogoUrl(clientId);
  }

  toggleMenu(): void {
    this.menuOpen.update((o) => !o);
  }
  closeMenu(): void {
    this.menuOpen.set(false);
  }

  qty(id: string): number {
    return this.cart()[id] ?? 0;
  }
  inc(n: MenuNode): void {
    this.placed.set(false);
    this.orderError.set(null);
    this.cart.update((c) => ({ ...c, [n.id]: (c[n.id] ?? 0) + 1 }));
  }
  dec(id: string): void {
    this.cart.update((c) => {
      const next = { ...c };
      const q = (next[id] ?? 0) - 1;
      if (q <= 0) delete next[id];
      else next[id] = q;
      return next;
    });
  }

  /** Send the cart. Needs an open table + this device approved (else 409/403 explain why). */
  placeOrder(): void {
    if (this.placing() || this.cartCount() === 0) return;
    const token = this.storedToken();
    if (!token) {
      this.orderError.set('Please wait for the waiter to approve this table.');
      return;
    }
    const items = Object.entries(this.cart()).map(([menuItemId, quantity]) => ({
      menuItemId,
      quantity,
    }));
    // ONLINE self-pay: the gateway sends the browser back here with ?ref=
    const returnUrl = `${window.location.origin}/customer/order-point/${this.opId}/payment-return`;
    this.placing.set(true);
    this.orderError.set(null);
    this.service.placeOrder(this.opId, token, items, returnUrl).subscribe({
      next: (result) => {
        this.cart.set({});
        sessionStorage.removeItem(this.cartKey);
        if (result.paymentUrl) {
          // pay first — the order is created once the gateway confirms
          window.location.href = result.paymentUrl;
          return;
        }
        this.placing.set(false);
        this.placed.set(true);
      },
      error: (err: HttpErrorResponse) => {
        this.placing.set(false);
        this.orderError.set(
          err.status === 409
            ? 'This table is not open yet — please ask a waiter to open it.'
            : err.status === 403
              ? 'Ordering is not available — please ask your waiter.'
              : 'Could not place your order. Please try again.',
        );
      },
    });
  }
}
