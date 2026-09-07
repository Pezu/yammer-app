import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import {
  Order,
  OrderItem,
  OrderPointOption,
  OrderReportService,
  WaiterOption,
} from './order-report.service';
import { ComboOption, FilterCombo } from './filter-combo';
import { Client, ClientService } from '../clients/client.service';
import { Location, LocationService, defaultLocation } from '../locations/location.service';
import { AuthService } from '../../../../core/auth.service';
import { ComboBox } from '../../../../shared/combo-box';

/**
 * The orders report (ported from old yammer, minus events): a paginated, filterable
 * order list on the left and an editable order detail on the right — unpaid item
 * quantities can be corrected, and a fully-unpaid order can be deleted.
 */
@Component({
  selector: 'app-orders-report-page',
  imports: [FilterCombo, ComboBox],
  templateUrl: './orders-report-page.html',
  styleUrl: './orders-report-page.scss',
})
export class OrdersReportPage {
  private readonly service = inject(OrderReportService);
  private readonly clientService = inject(ClientService);
  private readonly locationService = inject(LocationService);
  private readonly auth = inject(AuthService);

  readonly isSuper = this.auth.isSuper;
  readonly ownClientId = computed(() => (this.isSuper() ? '' : this.auth.clientId() ?? ''));

  // `orders` holds the CURRENT page only; `total` is the server-side row count.
  readonly orders = signal<Order[]>([]);
  readonly total = signal(0);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly page = signal(1);

  readonly pageSizes = [10, 50, 100];
  readonly pageSize = signal(10);
  readonly comboOpen = signal(false);

  // --- context: SUPER picks a client; everyone picks a location ---
  readonly clients = signal<Client[]>([]);
  readonly locations = signal<Location[]>([]);
  readonly clientFilter = signal<string>('');
  readonly locationFilter = signal<string>('');
  readonly headerLocationOptions = computed(() =>
    this.locationsFor(this.isSuper() ? this.clientFilter() : this.ownClientId()),
  );
  readonly showClientCombo = this.isSuper;
  readonly showLocationCombo = computed(() => !this.isSuper() || !!this.clientFilter());

  // --- column filters ---
  readonly fOrderNo = signal('');
  readonly fOrderPointId = signal('');
  readonly fWaiter = signal('');
  readonly fPaid = signal('');
  readonly orderPointOptions = signal<OrderPointOption[]>([]);
  readonly waiterOptions = signal<WaiterOption[]>([]);
  readonly orderPointComboOptions = computed<ComboOption[]>(() =>
    this.orderPointOptions().map((o) => ({ value: o.id, label: o.name })),
  );
  readonly waiterComboOptions = computed<ComboOption[]>(() =>
    this.waiterOptions().map((w) => ({ value: w.username, label: w.name })),
  );
  readonly paidOptions: ComboOption[] = [
    { value: 'NOT', label: 'Not paid' },
    { value: 'PAR', label: 'Partial' },
    { value: 'PAID', label: 'Paid' },
  ];

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.total() / this.pageSize())));
  readonly range = computed(() => {
    const total = this.total();
    if (total === 0) return '0';
    const start = (this.page() - 1) * this.pageSize() + 1;
    return `${start}–${Math.min(total, this.page() * this.pageSize())} of ${total}`;
  });

  // --- order detail (right panel, editable) ---
  readonly selected = signal<Order | null>(null);
  readonly detailTab = signal<'paid' | 'unpaid'>('unpaid');
  readonly editQty = signal<Map<string, number>>(new Map());
  readonly pendingDelete = signal(false);
  readonly saving = signal(false);

  readonly paidItems = computed(() => this.selected()?.items.filter((i) => i.paid) ?? []);
  readonly unpaidItems = computed(() => this.selected()?.items.filter((i) => !i.paid) ?? []);
  readonly canDelete = computed(() => this.selected()?.paid === 'NOT');
  readonly hasChanges = computed(() => {
    const sel = this.selected();
    if (!sel) return false;
    if (this.pendingDelete()) return true;
    const m = this.editQty();
    return sel.items.some((it) => !it.paid && (m.get(it.id) ?? it.quantity) !== it.quantity);
  });

  constructor() {
    this.clientService.list().subscribe({
      next: (clients) => {
        this.clients.set(clients);
        if (this.isSuper() && clients.length === 1 && !this.clientFilter()) {
          this.clientFilter.set(clients[0].id);
        }
      },
    });
    this.locationService.list().subscribe({
      next: (locations) => this.locations.set(locations),
    });
    effect(() => {
      const preferred = defaultLocation(this.headerLocationOptions());
      if (preferred && !this.locationFilter()) {
        this.locationFilter.set(preferred.id);
      }
    });
    // (Re)load whenever the selected location changes. The body runs untracked so the
    // effect depends ONLY on the location — not on the filter/page signals load() reads.
    effect(() => {
      const locationId = this.locationFilter();
      untracked(() => {
        this.resetFilters();
        this.page.set(1);
        this.selected.set(null);
        if (locationId) {
          this.loadFilterOptions();
          this.load();
        } else {
          this.orders.set([]);
          this.total.set(0);
          this.loading.set(false);
        }
      });
    });
  }

  selectClient(id: string): void {
    this.clientFilter.set(id);
    this.locationFilter.set('');
  }

  private locationsFor(clientId: string): Location[] {
    return clientId ? this.locations().filter((l) => l.clientId === clientId) : [];
  }

  private loadFilterOptions(): void {
    this.service.filterOptions(this.locationFilter()).subscribe({
      next: (o) => {
        this.orderPointOptions.set(o.orderPoints);
        this.waiterOptions.set(o.waiters);
      },
      error: () => {
        this.orderPointOptions.set([]);
        this.waiterOptions.set([]);
      },
    });
  }

  private load(): void {
    this.loading.set(true);
    this.service
      .listPaged(this.locationFilter(), this.page() - 1, this.pageSize(), {
        orderNo: this.fOrderNo() ? Number(this.fOrderNo()) : null,
        orderPointId: this.fOrderPointId() || null,
        waiter: this.fWaiter() || null,
        paid: this.fPaid() || null,
      })
      .subscribe({
        next: (res) => {
          this.orders.set(res.content);
          this.total.set(res.total);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Failed to load orders.');
          this.loading.set(false);
        },
      });
  }

  // --- filter setters: each resets to page 1 and reloads ---
  setOrderNo(v: string): void {
    this.fOrderNo.set(v.trim());
    this.applyFilter();
  }
  setOrderPoint(v: string): void {
    this.fOrderPointId.set(v);
    this.applyFilter();
  }
  setWaiter(v: string): void {
    this.fWaiter.set(v);
    this.applyFilter();
  }
  setPaid(v: string): void {
    this.fPaid.set(v);
    this.applyFilter();
  }
  private applyFilter(): void {
    this.page.set(1);
    this.selected.set(null);
    this.load();
  }
  private resetFilters(): void {
    this.fOrderNo.set('');
    this.fOrderPointId.set('');
    this.fWaiter.set('');
    this.fPaid.set('');
  }

  selectOrder(o: Order): void {
    this.selected.set(o);
    this.pendingDelete.set(false);
    this.editQty.set(this.qtyMap(o));
  }

  qtyOf(it: OrderItem): number {
    return this.editQty().get(it.id) ?? it.quantity;
  }
  inc(it: OrderItem): void {
    this.editQty.update((m) => new Map(m).set(it.id, this.qtyOf(it) + 1));
  }
  dec(it: OrderItem): void {
    this.editQty.update((m) => new Map(m).set(it.id, Math.max(0, this.qtyOf(it) - 1)));
  }

  markDelete(): void {
    if (this.canDelete()) this.pendingDelete.set(true);
  }
  undoDelete(): void {
    this.pendingDelete.set(false);
  }

  save(): void {
    const sel = this.selected();
    if (!sel || this.saving() || !this.hasChanges()) return;
    this.saving.set(true);
    this.error.set(null);

    const items = sel.items
      .filter((it) => !it.paid)
      .map((it) => ({ id: it.id, quantity: this.editQty().get(it.id) ?? it.quantity }));
    // nothing would remain (no paid items, all unpaid removed) → delete the whole order
    const remaining =
      sel.items.filter((it) => it.paid).length + items.filter((i) => i.quantity > 0).length;

    if (this.pendingDelete() || remaining === 0) {
      this.commitDelete(sel);
      return;
    }

    this.service.updateItems(sel.id, items).subscribe({
      next: (updated) => {
        this.orders.update((list) => list.map((o) => (o.id === updated.id ? updated : o)));
        this.selected.set(updated);
        this.editQty.set(this.qtyMap(updated));
        this.saving.set(false);
      },
      error: () => {
        this.error.set('Could not save changes.');
        this.saving.set(false);
      },
    });
  }

  private commitDelete(sel: Order): void {
    this.service.deleteOrder(sel.id).subscribe({
      next: () => {
        this.selected.set(null);
        this.saving.set(false);
        this.load(); // refresh the current page (and total) after removal
      },
      error: () => {
        this.error.set('Could not delete the order.');
        this.saving.set(false);
      },
    });
  }

  private qtyMap(o: Order): Map<string, number> {
    const m = new Map<string, number>();
    for (const it of o.items) if (!it.paid) m.set(it.id, it.quantity);
    return m;
  }

  money(v: number): string {
    return Number.isInteger(v)
      ? v.toLocaleString('en-US')
      : v.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }
  itemPrice(i: OrderItem, qty?: number): string {
    return this.money((i.price ?? 0) * (qty ?? i.quantity));
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'ORDERED':
        return 'ORD';
      case 'READY':
        return 'RDY';
      case 'DELIVERED':
        return 'DEL';
      case 'CANCELED':
        return 'CAN';
      default:
        return status.slice(0, 3).toUpperCase();
    }
  }

  prev(): void {
    if (this.page() <= 1) return;
    this.page.update((p) => Math.max(1, p - 1));
    this.load();
  }
  next(): void {
    if (this.page() >= this.totalPages()) return;
    this.page.update((p) => Math.min(this.totalPages(), p + 1));
    this.load();
  }

  toggleCombo(): void {
    this.comboOpen.update((o) => !o);
  }
  closeCombo(): void {
    this.comboOpen.set(false);
  }
  setPageSize(n: number): void {
    this.pageSize.set(n);
    this.page.set(1);
    this.comboOpen.set(false);
    this.load();
  }
}
