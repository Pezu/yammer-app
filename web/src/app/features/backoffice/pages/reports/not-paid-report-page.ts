import { Component, computed, effect, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { NotPaidReportRow, NotPaidReportService } from './not-paid-report.service';
import { Client, ClientService } from '../clients/client.service';
import { Location, LocationService, defaultLocation } from '../locations/location.service';
import { AuthService } from '../../../../core/auth.service';
import { ComboBox } from '../../../../shared/combo-box';

/**
 * Reports → Not paid: consumption closed without money — every settlement taken with the PROTOCOL
 * or PO payment type in the period, newest first, each expandable to its product lines.
 */
@Component({
  selector: 'app-not-paid-report-page',
  imports: [DatePipe, DecimalPipe, ComboBox],
  templateUrl: './not-paid-report-page.html',
  styleUrl: './not-paid-report-page.scss',
})
export class NotPaidReportPage {
  private readonly reportService = inject(NotPaidReportService);
  private readonly clientService = inject(ClientService);
  private readonly locationService = inject(LocationService);
  private readonly auth = inject(AuthService);

  readonly isSuper = this.auth.isSuper;
  readonly ownClientId = computed(() => (this.isSuper() ? '' : this.auth.clientId() ?? ''));

  readonly rows = signal<NotPaidReportRow[]>([]);
  readonly clients = signal<Client[]>([]);
  readonly locations = signal<Location[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly clientFilter = signal<string>('');
  readonly locationFilter = signal<string>('');
  readonly from = signal<string>(today());
  readonly to = signal<string>(today());
  readonly headerLocationOptions = computed(() =>
    this.locationsFor(this.isSuper() ? this.clientFilter() : this.ownClientId()),
  );
  readonly showClientCombo = this.isSuper;
  readonly showLocationCombo = computed(() => !this.isSuper() || !!this.clientFilter());

  /** Rows whose product lines are unfolded (all are, by default — the lines are the point of this report). */
  readonly collapsed = signal<Set<string>>(new Set());

  readonly totalAmount = computed(() => this.rows().reduce((s, r) => s + r.amount, 0));
  readonly totalItems = computed(() =>
    this.rows().reduce((s, r) => s + r.items.reduce((q, i) => q + i.quantity, 0), 0),
  );

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
      const from = this.from();
      const to = this.to();
      if (locationId && from && to) {
        this.load(locationId, from, to);
      } else {
        this.rows.set([]);
      }
    });
  }

  selectClient(id: string): void {
    this.clientFilter.set(id);
    this.locationFilter.set('');
  }

  quick(range: 'today' | 'yesterday' | 'week' | 'month'): void {
    const now = new Date();
    const d = (n: number) => iso(new Date(now.getFullYear(), now.getMonth(), now.getDate() + n));
    switch (range) {
      case 'today':
        this.from.set(d(0));
        this.to.set(d(0));
        break;
      case 'yesterday':
        this.from.set(d(-1));
        this.to.set(d(-1));
        break;
      case 'week':
        this.from.set(d(-6));
        this.to.set(d(0));
        break;
      case 'month':
        this.from.set(iso(new Date(now.getFullYear(), now.getMonth(), 1)));
        this.to.set(d(0));
        break;
    }
  }

  toggle(id: string): void {
    const next = new Set(this.collapsed());
    if (next.has(id)) {
      next.delete(id);
    } else {
      next.add(id);
    }
    this.collapsed.set(next);
  }

  itemCount(row: NotPaidReportRow): number {
    return row.items.reduce((q, i) => q + i.quantity, 0);
  }

  isOpen(id: string): boolean {
    return !this.collapsed().has(id);
  }

  private load(locationId: string, from: string, to: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.reportService.list(locationId, from, to).subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.collapsed.set(new Set());
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load the not-paid report.');
        this.loading.set(false);
      },
    });
  }

  private locationsFor(clientId: string): Location[] {
    return clientId ? this.locations().filter((l) => l.clientId === clientId) : [];
  }
}

function today(): string {
  return iso(new Date());
}

function iso(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}
