import { Component, OnDestroy, computed, effect, inject, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Dashboard, DashboardBucket, DashboardReportService } from './dashboard-report.service';
import { Client, ClientService } from '../clients/client.service';
import { Location, LocationService, defaultLocation } from '../locations/location.service';
import { Integration, IntegrationService } from '../integrations/integration.service';
import { AuthService } from '../../../../core/auth.service';
import { ComboBox } from '../../../../shared/combo-box';

/** A chart-ready point: x/y in SVG units plus the bucket it came from. */
interface Pt {
  x: number;
  y: number;
  bucket: DashboardBucket;
}

/**
 * Reports → Dashboard: one location, a date range (today by default), everything at once —
 * KPI tiles, the sales timeline (hand-drawn SVG, as in the old app), tables, products,
 * waiters, payment types and the final report with its thermal print.
 */
@Component({
  selector: 'app-dashboard-page',
  imports: [DecimalPipe, ComboBox],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.scss',
})
export class DashboardPage implements OnDestroy {
  /** Watcher mode: no print/export actions, no printer lookup, and the data refreshes on its own. */
  readonly readOnly = input(false);
  /** Auto-refresh period in seconds (0 = off). */
  readonly refreshEverySeconds = input(0);
  private refreshTimer: ReturnType<typeof setInterval> | undefined;

  ngOnDestroy(): void {
    clearInterval(this.refreshTimer);
  }

  private readonly reportService = inject(DashboardReportService);
  private readonly clientService = inject(ClientService);
  private readonly locationService = inject(LocationService);
  private readonly integrationService = inject(IntegrationService);
  private readonly auth = inject(AuthService);

  readonly isSuper = this.auth.isSuper;
  readonly ownClientId = computed(() => (this.isSuper() ? '' : this.auth.clientId() ?? ''));

  readonly data = signal<Dashboard | null>(null);
  readonly clients = signal<Client[]>([]);
  readonly locations = signal<Location[]>([]);
  readonly printers = signal<Integration[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);

  readonly clientFilter = signal<string>('');
  readonly locationFilter = signal<string>('');
  readonly from = signal<string>(today());
  readonly to = signal<string>(today());
  readonly headerLocationOptions = computed(() =>
    this.locationsFor(this.isSuper() ? this.clientFilter() : this.ownClientId()),
  );
  readonly showClientCombo = this.isSuper;
  readonly showLocationCombo = computed(() => !this.isSuper() || !!this.clientFilter());

  /** Sales chart tab: cumulative amounts, or orders per bucket. */
  readonly chartTab = signal<'amount' | 'orders'>('amount');

  constructor() {
    this.clientService.list().subscribe({
      next: (clients) => {
        this.clients.set(clients);
        if (this.isSuper() && clients.length === 1 && !this.clientFilter()) {
          this.clientFilter.set(clients[0].id);
        }
      },
    });
    this.locationService.list().subscribe({ next: (locations) => this.locations.set(locations) });
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
        this.data.set(null);
      }
    });
    effect(() => {
      clearInterval(this.refreshTimer);
      const every = this.refreshEverySeconds();
      if (every > 0) {
        this.refreshTimer = setInterval(() => {
          const locationId = this.locationFilter();
          if (locationId && !this.loading()) this.load(locationId, this.from(), this.to(), true);
        }, every * 1000);
      }
    });
    effect(() => {
      const locationId = this.locationFilter();
      if (locationId && !this.readOnly()) {
        this.integrationService.list(locationId, 'PRINTER').subscribe({
          next: (list) => {
            this.printers.set(list);
            if (!this.printerId() && list.length) this.printerId.set(list[0].id);
          },
        });
      } else {
        this.printers.set([]);
      }
    });
  }

  selectClient(id: string): void {
    this.clientFilter.set(id);
    this.locationFilter.set('');
  }

  /** Quick ranges: today, yesterday, last 7 days, this month. */
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

  private load(locationId: string, from: string, to: string, silent = false): void {
    if (!silent) this.loading.set(true);
    this.error.set(null);
    this.reportService.load(locationId, from, to).subscribe({
      next: (data) => {
        this.data.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load the dashboard.');
        this.loading.set(false);
      },
    });
  }

  private locationsFor(clientId: string): Location[] {
    return clientId ? this.locations().filter((l) => l.clientId === clientId) : [];
  }

  // --- totals for the table footers ---

  readonly tableTotals = computed(() => sumRows(this.data()?.tables ?? [], ['ordered', 'paidCash', 'paidCard', 'paidProtocol', 'paidOther', 'tips', 'remaining']));
  readonly waiterTotals = computed(() => sumRows(this.data()?.waiters ?? [], ['orders', 'sales', 'paidCash', 'paidCard', 'paidProtocol', 'paidOther', 'tipsCash', 'tipsCard', 'unsettled']));
  readonly finalTotals = computed(() => sumRows(this.data()?.finalReport ?? [], ['paidCard', 'paidCash', 'tipCard', 'tipCash', 'total']));
  readonly productTotals = computed(() => sumRows(this.data()?.products ?? [], ['quantity', 'sales']));

  // --- sales chart (inline SVG, ported from the old sales widget) ---

  readonly W = 820;
  readonly H = 300;
  private readonly padL = 52;
  private readonly padR = 14;
  private readonly padT = 14;
  private readonly padB = 30;

  /** Buckets trimmed to the first…last active one (empty edges make a flat, unreadable chart). */
  readonly chartRows = computed(() => {
    const s = this.data()?.series ?? [];
    const active = (b: DashboardBucket) => b.ordered > 0 || b.paid > 0 || b.orders > 0;
    let first = s.findIndex(active);
    if (first < 0) return [];
    let last = s.length - 1;
    while (last > first && !active(s[last])) last--;
    return s.slice(first, last + 1);
  });

  readonly chart = computed(() => {
    const rows = this.chartRows();
    const tab = this.chartTab();
    const ordered = cumulative(rows.map((r) => r.ordered));
    const paid = cumulative(rows.map((r) => r.paid));
    const orders = rows.map((r) => r.orders);
    const maxY = Math.max(1, tab === 'amount' ? Math.max(...ordered, ...paid) : Math.max(...orders));
    const step = niceStep(maxY);
    const top = Math.ceil(maxY / step) * step;
    const innerW = this.W - this.padL - this.padR;
    const innerH = this.H - this.padT - this.padB;
    const n = rows.length;
    const x = (i: number) => this.padL + (n <= 1 ? innerW / 2 : (i / (n - 1)) * innerW);
    const y = (v: number) => this.padT + innerH - (v / top) * innerH;
    const line = (vals: number[]) => vals.map((v, i) => `${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ');
    const grid: { y: number; label: string }[] = [];
    for (let v = 0; v <= top + 1e-9; v += step) grid.push({ y: y(v), label: fmtAxis(v) });
    const every = Math.max(1, Math.ceil(n / 12));
    const daily = (this.data()?.bucketMinutes ?? 0) >= 24 * 60;
    const ticks = rows.map((r, i) => ({ x: x(i), label: axisTime(r.at, daily), show: i % every === 0 || i === n - 1 }));
    const barW = n ? Math.max(2, (innerW / n) * 0.6) : 0;
    const bars: Pt[] = rows.map((r, i) => ({ x: x(i) - barW / 2, y: y(r.orders), bucket: r }));
    return {
      orderedLine: line(ordered),
      paidLine: line(paid),
      bars,
      barW,
      baseY: y(0),
      grid,
      ticks,
      orderedTotal: ordered[ordered.length - 1] ?? 0,
      paidTotal: paid[paid.length - 1] ?? 0,
      ordersTotal: orders.reduce((a, b) => a + b, 0),
    };
  });

  // --- final report: print + export ---

  readonly printerId = signal<string>('');
  readonly printing = signal(false);
  readonly printerOptions = computed(() => this.printers().map((p) => ({ id: p.id, name: p.name })));

  printFinal(): void {
    const locationId = this.locationFilter();
    const printerId = this.printerId();
    if (!locationId || !printerId || this.printing()) return;
    this.printing.set(true);
    this.notice.set(null);
    this.reportService.printFinal(locationId, this.from(), this.to(), printerId).subscribe({
      next: () => {
        this.printing.set(false);
        this.notice.set('Final report sent to the printer.');
      },
      error: (err) => {
        this.printing.set(false);
        this.error.set(err?.error?.message || 'Could not print the final report.');
      },
    });
  }

  /** Excel-openable export of the final report (an HTML table, as the old app did). */
  exportFinal(): void {
    const rows = this.data()?.finalReport ?? [];
    const t = this.finalTotals();
    const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;');
    const body = rows
      .map((r) => `<tr><td>${esc(r.waiter)}</td><td>${r.paidCard}</td><td>${r.paidCash}</td><td>${r.tipCard}</td><td>${r.tipCash}</td><td>${r.total}</td></tr>`)
      .join('');
    const html = `<table border="1"><tr><th>Waiter</th><th>Paid card</th><th>Paid cash</th><th>Tip card</th><th>Tip cash</th><th>Total</th></tr>${body}<tr><td>Total</td><td>${t['paidCard']}</td><td>${t['paidCash']}</td><td>${t['tipCard']}</td><td>${t['tipCash']}</td><td>${t['total']}</td></tr></table>`;
    const blob = new Blob([`﻿<html><head><meta charset="utf-8"></head><body>${html}</body></html>`], { type: 'application/vnd.ms-excel' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `final-report-${this.from()}_${this.to()}.xls`;
    a.click();
    URL.revokeObjectURL(url);
  }
}

function today(): string {
  return iso(new Date());
}

function iso(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

function sumRows<T extends object>(rows: T[], keys: (keyof T)[]): Record<string, number> {
  const out: Record<string, number> = {};
  for (const k of keys) {
    out[k as string] = Math.round(rows.reduce((s, r) => s + (Number(r[k]) || 0), 0) * 100) / 100;
  }
  return out;
}

function cumulative(values: number[]): number[] {
  let acc = 0;
  return values.map((v) => (acc = Math.round((acc + v) * 100) / 100));
}

/** A "nice" axis step for the value range: 1 / 2 / 2.5 / 5 × 10^k, aiming at ~5 gridlines. */
function niceStep(max: number): number {
  const raw = max / 5;
  const mag = Math.pow(10, Math.floor(Math.log10(raw)));
  for (const m of [1, 2, 2.5, 5, 10]) {
    if (m * mag >= raw) return m * mag;
  }
  return 10 * mag;
}

function fmtAxis(v: number): string {
  return v >= 1000 ? `${Math.round(v / 100) / 10}k` : String(Math.round(v * 10) / 10);
}

/** "yyyy-MM-ddTHH:mm" → "HH:mm", or "dd.MM" when the buckets are whole days. */
function axisTime(at: string, daily: boolean): string {
  const [date, time] = at.split('T');
  return daily ? `${date.slice(8, 10)}.${date.slice(5, 7)}` : (time ?? date);
}
