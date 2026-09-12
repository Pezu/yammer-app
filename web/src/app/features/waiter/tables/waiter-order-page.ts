import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { DecimalPipe, Location } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  MenuNode,
  MenuOption,
  OrderPointMenu,
  ProductOption,
  WaiterOrderPointService,
} from './waiter-order-point.service';
import { WaiterMenuCacheService } from '../waiter-menu-cache.service';
import {
  PaymentType,
  PaymentTypeService,
} from '../../backoffice/pages/payment-types/payment-type.service';
import { OrderPointService } from '../../backoffice/pages/order-points/order-point.service';

type TipMode = 'none' | 'p10' | 'p12' | 'p15' | 'customPct' | 'customAmt';

function round2(v: number): number {
  return Math.round(v * 100) / 100;
}
import { ToastService } from '../../../core/toast.service';
import { I18nService } from '../../../core/i18n.service';

interface CartLine {
  menuItemId: string;
  name: string;
  price: number;
  quantity: number;
}

@Component({
  selector: 'app-waiter-order-page',
  imports: [DecimalPipe],
  templateUrl: './waiter-order-page.html',
  styleUrl: './waiter-order-page.scss',
})
export class WaiterOrderPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly service = inject(WaiterOrderPointService);
  private readonly menuCache = inject(WaiterMenuCacheService);
  private readonly toast = inject(ToastService);
  private readonly paymentTypeService = inject(PaymentTypeService);
  private readonly orderPointService = inject(OrderPointService);
  readonly t = inject(I18nService).t;

  // --- pay as you order (keepOpen = false, e.g. bars): the payment is chosen before placing ---

  private readonly paymentTypes = signal<PaymentType[]>([]);
  readonly payOpen = signal(false);
  // tip as in the table pay sheet: presets + custom % / custom RON
  readonly tipMode = signal<TipMode>('none');
  readonly tipCustomPercent = signal<number | null>(null);
  readonly tipCustomAmount = signal<number | null>(null);
  readonly computedTip = computed(() => {
    const base = this.total() || 0;
    let tip = 0;
    switch (this.tipMode()) {
      case 'p10':
        tip = base * 0.1;
        break;
      case 'p12':
        tip = base * 0.12;
        break;
      case 'p15':
        tip = base * 0.15;
        break;
      case 'customPct': {
        const p = this.tipCustomPercent();
        if (p != null && !isNaN(p)) tip = base * (p / 100);
        break;
      }
      case 'customAmt': {
        const a = this.tipCustomAmount();
        if (a != null && !isNaN(a)) tip = a;
        break;
      }
    }
    return Math.max(0, round2(tip));
  });
  /** The point's accepted payment types, as buttons. */
  readonly payOptions = computed(() => {
    const byId = new Map(this.paymentTypes().map((p) => [p.id, p.type]));
    return (this.menu()?.paymentTypeIds ?? []).map((id) => ({ id, name: byId.get(id) ?? '?' }));
  });
  /** Older cached menus carry no flag: treat them as tabs (the safe default). */
  readonly payNow = computed(() => this.menu()?.keepOpen === false);
  readonly discountPercent = computed(() => this.menu()?.discountPercent ?? 0);
  readonly discountAmount = computed(() => round2((this.total() * this.discountPercent()) / 100));
  readonly chargeTotal = computed(() => round2(this.total() - this.discountAmount()));
  readonly payTotal = computed(() => round2(this.chargeTotal() + this.computedTip()));

  setTip(mode: TipMode): void {
    this.tipMode.set(mode);
  }

  private readonly id = this.route.snapshot.paramMap.get('id') ?? '';
  private readonly stateName = (history.state?.name as string) ?? '';

  readonly menu = signal<OrderPointMenu | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  /** The currently displayed menu tree (the default menu's, or a switched-to menu's). */
  readonly items = signal<MenuNode[]>([]);
  /** Which menu is selected in the header switcher. */
  readonly selectedMenuId = signal<string | null>(null);
  readonly menuComboOpen = signal(false);
  readonly menuOptions = computed<MenuOption[]>(() => this.menu()?.menus ?? []);
  readonly selectedMenuName = computed(
    () => this.menuOptions().find((m) => m.id === this.selectedMenuId())?.name ?? this.t('order.menu'),
  );

  // --- product search (across all of the location's menus, grouped by menu) ---
  readonly searchQuery = signal('');
  private readonly allProducts = computed<ProductOption[]>(() => this.menu()?.products ?? []);
  readonly hasProducts = computed(() => this.allProducts().length > 0);

  /** Matches grouped by menu, in the menu switcher's order; menus with no match are omitted. */
  readonly searchGroups = computed<{ menuId: string; menuName: string; items: ProductOption[] }[]>(() => {
    const q = this.searchQuery().trim().toLowerCase();
    if (!q) {
      return [];
    }
    const byMenu = new Map<string, ProductOption[]>();
    for (const p of this.allProducts()) {
      if (!p.name.toLowerCase().includes(q)) {
        continue;
      }
      const list = byMenu.get(p.menuId);
      if (list) list.push(p);
      else byMenu.set(p.menuId, [p]);
    }
    return this.menuOptions()
      .filter((m) => byMenu.has(m.id))
      .map((m) => ({ menuId: m.id, menuName: m.name, items: byMenu.get(m.id)! }));
  });

  /**
   * Both the drill-down path (?c=catId1,catId2) and the cart view (?s=1) live in
   * the URL, so each is a real history entry — hardware/router back walks one step
   * up the menu (and closes the cart) instead of leaving the page.
   */
  private readonly query = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });
  readonly summaryOpen = computed(() => this.query().get('s') === '1');
  private readonly pathIds = computed(() => {
    const c = this.query().get('c');
    return c ? c.split(',') : [];
  });

  readonly stack = computed<MenuNode[]>(() => {
    const result: MenuNode[] = [];
    let level = this.items();
    for (const id of this.pathIds()) {
      const node = level.find((n) => n.id === id && !n.orderable);
      if (!node) {
        break;
      }
      result.push(node);
      level = node.children ?? [];
    }
    return result;
  });

  readonly currentItems = computed<MenuNode[]>(() => {
    const s = this.stack();
    const items = s.length === 0 ? this.items() : s[s.length - 1].children ?? [];
    // categories (orderable=false) before products among siblings
    return [...items].sort((a, b) => Number(a.orderable) - Number(b.orderable));
  });

  // Bound via [innerHTML], which auto-sanitizes the plain string (no bypass → no stored XSS).
  readonly titleHtml = computed<string>(() => {
    if (this.summaryOpen()) {
      return this.t('order.cart');
    }
    const s = this.stack();
    return s.length
      ? s[s.length - 1].name
      : this.menu()?.orderPointName || this.stateName || this.t('order.menu');
  });

  // --- cart ---
  readonly cart = signal<CartLine[]>([]);
  readonly placing = signal(false);
  readonly placeError = signal<string | null>(null);
  readonly totalItems = computed(() => this.cart().reduce((s, l) => s + l.quantity, 0));
  readonly total = computed(() => this.cart().reduce((s, l) => s + l.price * l.quantity, 0));

  constructor() {
    this.paymentTypeService.list().subscribe({ next: (types) => this.paymentTypes.set(types) });
    // Loads once on open (from the cache when available) and again whenever the
    // cache version is bumped.
    effect(() => {
      this.menuCache.version();
      untracked(() => this.load());
    });
  }

  /**
   * The point's own settings (keep open, payment types) are edited in the backoffice and must
   * not be as stale as the cached menu: overlay them from the live assigned-tables list.
   */
  private refreshPointFlags(): void {
    this.orderPointService.assigned().subscribe({
      next: (points) => {
        const p = points.find((x) => x.id === this.id);
        if (p) {
          this.menu.update((m) => (m ? { ...m, keepOpen: p.keepOpen, paymentTypeIds: p.paymentTypeIds } : m));
        }
      },
      error: () => {
        /* keep the cached flags */
      },
    });
  }

  /** Load the order point's menu through the cache, keeping the selected menu if it still exists. */
  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.menuCache.menu(this.id).subscribe({
      next: (m) => {
        this.menu.set(m);
        this.refreshPointFlags();
        const previous = this.selectedMenuId();
        const selected =
          previous && m.menus.some((o) => o.id === previous) ? previous : m.menuId;
        this.selectedMenuId.set(selected);
        if (selected && selected !== m.menuId) {
          this.loadTree(selected);
        } else {
          this.items.set(m.items);
          this.loading.set(false);
        }
      },
      error: () => {
        this.error.set(this.t('order.loadFailed'));
        this.loading.set(false);
      },
    });
  }

  /** Load one menu's tree through the cache; optionally reset the drill-down to its root. */
  private loadTree(menuId: string, resetPath = false): void {
    this.loading.set(true);
    this.error.set(null);
    this.menuCache.menuTree(menuId).subscribe({
      next: (tree) => {
        this.items.set(tree);
        this.loading.set(false);
        if (resetPath) {
          this.toMenu([]); // back to the root of the newly-selected menu
        }
      },
      error: () => {
        this.error.set(this.t('order.loadFailed'));
        this.loading.set(false);
      },
    });
  }

  // --- menu switcher (header combo) ---

  toggleMenuCombo(): void {
    this.menuComboOpen.update((open) => !open);
  }

  closeMenuCombo(): void {
    this.menuComboOpen.set(false);
  }

  /** Switch to another menu of the location: load its tree and reset the drill-down to the root. */
  selectMenu(menuId: string): void {
    this.menuComboOpen.set(false);
    if (menuId === this.selectedMenuId()) {
      return;
    }
    this.selectedMenuId.set(menuId);
    this.loadTree(menuId, true);
  }

  onSearch(event: Event): void {
    this.searchQuery.set((event.target as HTMLInputElement).value);
  }

  clearSearch(): void {
    this.searchQuery.set('');
  }

  /** Add a searched product straight to the cart (stays open so several can be added). */
  addProduct(p: ProductOption): void {
    this.increase({ id: p.id, name: p.name, orderable: true, price: p.price, vatTypeId: null, children: [] });
    this.toast.show(this.t('order.added'));
  }

  isCategory(n: MenuNode): boolean {
    return !n.orderable;
  }
  qty(itemId: string): number {
    return this.cart().find((l) => l.menuItemId === itemId)?.quantity ?? 0;
  }

  tap(n: MenuNode): void {
    if (this.isCategory(n)) {
      const path = [...this.pathIds(), n.id];
      this.router.navigate([], { relativeTo: this.route, queryParams: { c: path.join(',') } });
    } else if (n.orderable) {
      this.increase(n);
    }
  }

  increase(n: MenuNode): void {
    this.cart.update((list) => {
      const existing = list.find((l) => l.menuItemId === n.id);
      if (existing) {
        return list.map((l) => (l.menuItemId === n.id ? { ...l, quantity: l.quantity + 1 } : l));
      }
      return [...list, { menuItemId: n.id, name: n.name, price: n.price ?? 0, quantity: 1 }];
    });
  }

  decrease(id: string, event?: Event): void {
    event?.stopPropagation();
    this.cart.update((list) =>
      list.flatMap((l) => {
        if (l.menuItemId !== id) {
          return [l];
        }
        return l.quantity > 1 ? [{ ...l, quantity: l.quantity - 1 }] : [];
      }),
    );
  }

  /** Open the cart summary (a history entry, so back closes it). */
  openSummary(): void {
    if (!this.cart().length) {
      return;
    }
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { s: '1' },
      queryParamsHandling: 'merge',
    });
  }

  /** Tab points place right away; pay-now points ask for the payment first. */
  placeOrder(): void {
    if (!this.cart().length || this.placing()) {
      return;
    }
    if (this.payNow()) {
      if (!this.payOptions().length) {
        this.placeError.set(this.t('order.noPaymentTypes'));
        return;
      }
      this.tipMode.set('none');
      this.tipCustomPercent.set(null);
      this.tipCustomAmount.set(null);
      this.placeError.set(null);
      this.payOpen.set(true);
      return;
    }
    this.submitOrder();
  }

  closePay(): void {
    this.payOpen.set(false);
  }

  /** Pay-now sheet: the chosen payment type places AND settles the order in one request. */
  payAndPlace(paymentTypeId: string): void {
    this.payOpen.set(false);
    this.submitOrder({ paymentTypeId, tip: this.computedTip() });
  }

  private submitOrder(pay?: { paymentTypeId: string; tip: number }): void {
    const lines = this.cart();
    this.placing.set(true);
    this.placeError.set(null);
    this.service
      .placeOrder(
        this.id,
        lines.map((l) => ({
          menuItemId: l.menuItemId,
          name: l.name,
          price: l.price,
          quantity: l.quantity,
        })),
        pay,
      )
      .subscribe({
        next: () => {
          this.cart.set([]);
          this.toast.show(this.t(pay ? 'order.placedPaid' : 'order.placed'));
          // rewrite the cart entry to the Tables URL (silently) so a back from the
          // table page exits the table instead of returning to the cart/menu
          this.location.replaceState('/waiter/tables');
          this.router.navigate(['/waiter/tables', this.id]);
        },
        error: () => {
          this.placing.set(false);
          this.placeError.set(this.t('order.placeFailed'));
        },
      });
  }

  /** Semantic back: cart → menu, category → parent category, menu root → the table page. */
  back(): void {
    if (this.summaryOpen()) {
      this.toMenu(this.pathIds());
    } else if (this.pathIds().length) {
      this.toMenu(this.pathIds().slice(0, -1));
    } else {
      this.router.navigate(['/waiter/tables', this.id]);
    }
  }

  private toMenu(path: string[]): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: path.length ? { c: path.join(',') } : {},
    });
  }
}
