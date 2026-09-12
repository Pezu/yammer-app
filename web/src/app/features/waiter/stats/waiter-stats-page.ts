import { Component, computed, effect, inject, signal } from '@angular/core';
import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { TableStats, WaiterOrderPointService } from '../tables/waiter-order-point.service';
import { I18nService } from '../../../core/i18n.service';
import { ComboBox } from '../../../shared/combo-box';

/**
 * /waiter/stats — the waiter's own takings, as in the old app: a grand "All tables" card plus
 * one card per table with Paid card / Paid cash / Total, Tip card / Tip cash / Tip and Unpaid;
 * a Protocol view lists comped tables. Scoped to one day (today by default).
 */
@Component({
  selector: 'app-waiter-stats-page',
  imports: [DecimalPipe, NgTemplateOutlet, ComboBox],
  template: `
    <header class="page-head">
      <input type="date" class="day" [value]="day()" (change)="day.set($any($event.target).value)" [attr.aria-label]="t('stats.day')" />
      @if (protocolRows().length) {
        <div class="view-combo">
          <app-combo-box [options]="viewOptions()" [value]="view()" (valueChange)="view.set($any($event))" />
        </div>
      }
    </header>

    <section class="page-body">
      @if (loading()) {
        <p class="state">{{ t('common.loading') }}</p>
      } @else if (error()) {
        <p class="state err">{{ error() }}</p>
      } @else if (rows().length === 0) {
        <p class="state">{{ t('stats.empty') }}</p>
      } @else if (view() === 'paid') {
        <div class="cards">
          <div class="card grand">
            <div class="card-head">{{ t('stats.allTables') }}</div>
            <ng-container [ngTemplateOutlet]="statBlock" [ngTemplateOutletContext]="{ s: normalTotals() }"></ng-container>
          </div>
          @for (r of normalRows(); track r.orderPointId) {
            <div class="card">
              <div class="card-head">{{ r.name }}</div>
              <ng-container [ngTemplateOutlet]="statBlock" [ngTemplateOutletContext]="{ s: r }"></ng-container>
            </div>
          }
        </div>
      } @else {
        <div class="cards">
          <div class="card grand proto">
            <div class="card-head">{{ t('stats.allProtocol') }}</div>
            <ng-container [ngTemplateOutlet]="protoBlock" [ngTemplateOutletContext]="{ s: protocolTotals() }"></ng-container>
          </div>
          @for (r of protocolRows(); track r.orderPointId) {
            <div class="card proto">
              <div class="card-head">{{ r.name }}</div>
              <ng-container [ngTemplateOutlet]="protoBlock" [ngTemplateOutletContext]="{ s: r }"></ng-container>
            </div>
          }
        </div>
      }
    </section>

    <ng-template #statBlock let-s="s">
      <div class="grp">
        <div class="row"><span>{{ t('stats.paidCard') }}</span><span>{{ s.paidCard | number: '1.2-2' }}</span></div>
        <div class="row"><span>{{ t('stats.paidCash') }}</span><span>{{ s.paidCash | number: '1.2-2' }}</span></div>
        <div class="row total"><span>{{ t('stats.total') }}</span><span>{{ s.paidCard + s.paidCash | number: '1.2-2' }}</span></div>
      </div>
      <div class="grp">
        <div class="row"><span>{{ t('stats.tipCard') }}</span><span>{{ s.tipCard | number: '1.2-2' }}</span></div>
        <div class="row"><span>{{ t('stats.tipCash') }}</span><span>{{ s.tipCash | number: '1.2-2' }}</span></div>
        <div class="row total"><span>{{ t('stats.tip') }}</span><span>{{ s.tipCard + s.tipCash | number: '1.2-2' }}</span></div>
      </div>
      <div class="row unpaid"><span>{{ t('stats.unpaid') }}</span><span>{{ s.unpaid | number: '1.2-2' }}</span></div>
    </ng-template>

    <ng-template #protoBlock let-s="s">
      <div class="row"><span>{{ t('stats.comped') }}</span><span>{{ s.settled | number: '1.2-2' }}</span></div>
      <div class="row unpaid"><span>{{ t('stats.unpaid') }}</span><span>{{ s.unpaid | number: '1.2-2' }}</span></div>
    </ng-template>
  `,
  styles: [
    `
      :host {
        display: block;
        background: #fff;
        min-height: calc(100vh - 56px);
        min-height: calc(100dvh - 56px);
      }
      .page-head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.75rem;
        padding: 0.85rem 1rem 0.6rem;
      }
      .day {
        padding: 0.45rem 0.6rem;
        font: inherit;
        font-size: 0.9rem;
        color: var(--text);
        border: 1px solid var(--border);
        border-radius: 8px;
        background: #fff;
      }
      .view-combo {
        min-width: 9rem;
      }
      .page-body {
        padding: 0.25rem 1rem 1.5rem;
      }
      .state {
        margin: 2rem 0;
        text-align: center;
        color: var(--muted);
        font-size: 0.95rem;
      }
      .state.err {
        color: var(--danger);
      }
      .cards {
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
      }
      .card {
        border: 1px solid var(--border);
        border-radius: 12px;
        padding: 0.7rem 0.9rem;
      }
      .card.grand {
        border-color: var(--primary);
        background: rgba(52, 84, 209, 0.04);
      }
      .card.proto {
        border-color: #d9b08c;
      }
      .card.grand.proto {
        border-color: #c98a4b;
        background: rgba(201, 138, 75, 0.06);
      }
      .card-head {
        font-weight: 800;
        font-size: 0.95rem;
        text-transform: uppercase;
        letter-spacing: 0.03em;
        margin-bottom: 0.4rem;
      }
      .grp {
        padding: 0.25rem 0;
      }
      .grp + .grp {
        border-top: 1px solid var(--border);
      }
      .row {
        display: flex;
        justify-content: space-between;
        align-items: baseline;
        padding: 0.2rem 0;
        font-size: 0.85rem;
      }
      .row span:first-child {
        color: var(--muted);
      }
      .row span:last-child {
        font-variant-numeric: tabular-nums;
      }
      .row.total span {
        font-weight: 800;
        color: var(--text);
      }
      .row.unpaid {
        margin-top: 0.35rem;
        padding-top: 0.45rem;
        border-top: 1px solid var(--border);
        font-weight: 700;
      }
      .row.unpaid span:first-child {
        color: var(--text);
      }
    `,
  ],
})
export class WaiterStatsPage {
  private readonly service = inject(WaiterOrderPointService);
  readonly t = inject(I18nService).t;

  readonly rows = signal<TableStats[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly day = signal(today());
  readonly view = signal<'paid' | 'protocol'>('paid');
  readonly viewOptions = computed(() => [
    { id: 'paid', name: this.t('stats.paid') },
    { id: 'protocol', name: this.t('stats.protocol') },
  ]);

  readonly normalRows = computed(() => this.rows().filter((r) => !r.protocol));
  readonly protocolRows = computed(() => this.rows().filter((r) => r.protocol));

  readonly normalTotals = computed(() => {
    const r = this.normalRows();
    return {
      paidCard: r.reduce((s, x) => s + x.paidCard, 0),
      paidCash: r.reduce((s, x) => s + x.paidCash, 0),
      tipCard: r.reduce((s, x) => s + x.tipCard, 0),
      tipCash: r.reduce((s, x) => s + x.tipCash, 0),
      unpaid: r.reduce((s, x) => s + x.unpaid, 0),
    };
  });
  readonly protocolTotals = computed(() => {
    const r = this.protocolRows();
    return {
      settled: r.reduce((s, x) => s + x.settled, 0),
      unpaid: r.reduce((s, x) => s + x.unpaid, 0),
    };
  });

  constructor() {
    effect(() => {
      const day = this.day();
      if (day) this.load(day);
    });
  }

  private load(day: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.myStats(day, day).subscribe({
      next: (rows) => {
        this.rows.set(rows);
        if (this.view() === 'protocol' && !rows.some((r) => r.protocol)) this.view.set('paid');
        this.loading.set(false);
      },
      error: () => {
        this.error.set(this.t('stats.loadFailed'));
        this.loading.set(false);
      },
    });
  }
}

function today(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}
