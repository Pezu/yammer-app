import { Component, computed, effect, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { PaymentReportRow, PaymentReportService } from './payment-report.service';
import { Client, ClientService } from '../clients/client.service';
import { Location, LocationService, defaultLocation } from '../locations/location.service';
import { AuthService } from '../../../../core/auth.service';
import { ComboBox } from '../../../../shared/combo-box';

/** Payments taken at a location, newest first: table, waiter, amount, tip, total, type. */
@Component({
  selector: 'app-payments-report-page',
  imports: [DecimalPipe, ComboBox],
  templateUrl: './payments-report-page.html',
  styleUrl: './payments-report-page.scss',
})
export class PaymentsReportPage {
  private readonly reportService = inject(PaymentReportService);
  private readonly clientService = inject(ClientService);
  private readonly locationService = inject(LocationService);
  private readonly auth = inject(AuthService);

  readonly isSuper = this.auth.isSuper;
  readonly ownClientId = computed(() => (this.isSuper() ? '' : this.auth.clientId() ?? ''));

  readonly rows = signal<PaymentReportRow[]>([]);

  // --- pager (server-side: `rows` is the current page; totals come from the API over all rows) ---
  readonly page = signal(1);
  readonly pageSizes = [10, 50, 100];
  readonly pageSize = signal(10);
  readonly comboOpen = signal(false);
  readonly total = signal(0);
  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.total() / this.pageSize())));
  readonly pageRows = this.rows;
  readonly range = computed(() => {
    const total = this.total();
    if (total === 0) return '0';
    const start = (this.page() - 1) * this.pageSize() + 1;
    return `${start}–${Math.min(total, this.page() * this.pageSize())} of ${total}`;
  });

  prev(): void {
    if (this.page() <= 1) return;
    this.page.update((p) => p - 1);
    this.reload();
  }
  next(): void {
    if (this.page() >= this.totalPages()) return;
    this.page.update((p) => p + 1);
    this.reload();
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
    this.reload();
  }
  private reload(): void {
    const locationId = this.locationFilter();
    if (locationId) this.load(locationId);
  }
  readonly clients = signal<Client[]>([]);
  readonly locations = signal<Location[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly clientFilter = signal<string>('');
  readonly locationFilter = signal<string>('');
  readonly headerLocationOptions = computed(() =>
    this.locationsFor(this.isSuper() ? this.clientFilter() : this.ownClientId()),
  );
  readonly showClientCombo = this.isSuper;
  readonly showLocationCombo = computed(() => !this.isSuper() || !!this.clientFilter());

  /** Totals over ALL the location's payments (from the API), not just the visible page. */
  readonly totals = signal({ amount: 0, tip: 0, total: 0 });

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
    effect(() => {
      const locationId = this.locationFilter();
      if (locationId) {
        this.page.set(1);
        this.load(locationId);
      } else {
        this.rows.set([]);
        this.total.set(0);
      }
    });
  }

  selectClient(id: string): void {
    this.clientFilter.set(id);
    this.locationFilter.set('');
  }

  /** Payments with a fiscal action in flight (retry / resolve). */
  readonly busy = signal<Set<string>>(new Set());

  /** Re-issue a FAILED receipt; reload after the bridge round-trip so the new status shows. */
  retry(paymentId: string): void {
    if (this.busy().has(paymentId)) return;
    this.busy.update((s) => new Set(s).add(paymentId));
    this.reportService.retryFiscal(paymentId).subscribe({
      next: () => setTimeout(() => this.clearBusy(paymentId, true), 3000),
      error: () => this.clearBusy(paymentId, false),
    });
  }

  /** Operator verdict on an UNKNOWN receipt, after checking the register. */
  resolveUnknown(paymentId: string, printed: boolean): void {
    if (this.busy().has(paymentId)) return;
    let receiptNumber: string | null = null;
    if (printed) {
      receiptNumber = window.prompt('Receipt number from the register (optional):')?.trim() || null;
    }
    this.busy.update((s) => new Set(s).add(paymentId));
    this.reportService.resolveUnknownFiscal(paymentId, printed, receiptNumber).subscribe({
      next: () => this.clearBusy(paymentId, true),
      error: () => this.clearBusy(paymentId, false),
    });
  }

  private clearBusy(paymentId: string, reload: boolean): void {
    this.busy.update((s) => {
      const next = new Set(s);
      next.delete(paymentId);
      return next;
    });
    const locationId = this.locationFilter();
    if (reload && locationId) this.load(locationId);
    if (!reload) this.error.set('Fiscal action failed.');
  }

  load(locationId: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.reportService.page(locationId, this.page() - 1, this.pageSize()).subscribe({
      next: (res) => {
        this.rows.set(res.content);
        this.total.set(res.total);
        this.totals.set(res.totals);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load payments.');
        this.loading.set(false);
      },
    });
  }

  private locationsFor(clientId: string): Location[] {
    return clientId ? this.locations().filter((l) => l.clientId === clientId) : [];
  }
}
