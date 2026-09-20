import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { WaiterActiveOrder, WaiterOrderPointService } from '../tables/waiter-order-point.service';
import { I18nService } from '../../../core/i18n.service';
import { ComboBox } from '../../../shared/combo-box';
import { roTime } from '../../../shared/tz';

type Tab = 'ORDERED' | 'READY';

/**
 * /waiter/orders — the undelivered orders at the waiter's own tables, as in the old app:
 * one card per order (table, who placed it, time, lines, total) with a Delivered button on
 * the READY ones. The status tab is fetched server-side and kept in the URL (?tab=).
 */
@Component({
  selector: 'app-waiter-orders-page',
  imports: [DecimalPipe, ComboBox],
  template: `
    <header class="page-head">
      <div class="view-combo">
        <app-combo-box [options]="tabOptions()" [value]="tab()" (valueChange)="setTab($any($event))" />
      </div>
    </header>

    <section class="page-body">
      @if (loading()) {
        <p class="state">{{ t('common.loading') }}</p>
      } @else if (error()) {
        <p class="state err">{{ error() }}</p>
      } @else if (orders().length === 0) {
        <p class="state">{{ tab() === 'READY' ? t('orders.emptyReady') : t('orders.emptyOrdered') }}</p>
      } @else {
        @for (o of orders(); track o.id) {
          <article class="card">
            <div class="card-head">
              <span class="head-left">
                <span class="op">{{ o.orderPointName }}</span>
                @if (o.createdBy) {
                  <span class="waiter">{{ o.createdBy }}</span>
                }
              </span>
              <span class="time">{{ time(o.createdAt) }}</span>
            </div>
            <ul class="items">
              @for (it of o.items; track it.id) {
                <li>
                  <span class="qty">{{ it.quantity }}×</span>
                  <span class="iname" [innerHTML]="it.name"></span>
                </li>
              }
            </ul>
            <div class="card-foot">
              <span class="total">{{ o.total | number: '1.2-2' }} RON</span>
              @if (o.status === 'READY') {
                <button type="button" class="deliver" [disabled]="busy().has(o.id)" (click)="deliver(o)">
                  {{ t('orders.delivered') }}
                </button>
              }
            </div>
          </article>
        }
      }
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
        background: #fff;
        min-height: calc(100vh - 56px);
        min-height: calc(100dvh - 56px); /* fill the content area below the 56px topbar */
      }
      .page-head {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        padding: 0.85rem 1rem 0.6rem;
      }
      .view-combo {
        min-width: 9rem;
      }
      .page-body {
        padding: 0.5rem 0.75rem 1.5rem;
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
      .card {
        background: #fff;
        border: 1px solid var(--border);
        border-radius: 10px;
        padding: 0.7rem 0.8rem;
        margin-bottom: 0.6rem;
      }
      .card-head {
        display: flex;
        align-items: baseline;
        justify-content: space-between;
        gap: 0.5rem;
      }
      .head-left {
        display: inline-flex;
        align-items: baseline;
        gap: 0.45rem;
        min-width: 0;
      }
      .op {
        font-size: 0.98rem;
        font-weight: 800;
      }
      .waiter {
        font-size: 0.74rem;
        color: var(--muted);
      }
      .time {
        font-size: 0.78rem;
        color: var(--muted);
        font-variant-numeric: tabular-nums;
      }
      .items {
        list-style: none;
        margin: 0.5rem 0 0;
        padding: 0;
      }
      .items li {
        display: flex;
        gap: 0.45rem;
        font-size: 0.85rem;
        line-height: 1.8;
      }
      .qty {
        font-weight: 700;
        color: var(--text);
        min-width: 1.7rem;
      }
      .card-foot {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-top: 0.6rem;
        padding-top: 0.55rem;
        border-top: 1px solid var(--border);
      }
      .total {
        font-size: 0.86rem;
        font-weight: 800;
        font-variant-numeric: tabular-nums;
      }
      .deliver {
        padding: 0.4rem 0.9rem;
        font: inherit;
        font-size: 0.8rem;
        font-weight: 700;
        color: #0f9d63;
        background: #fff;
        border: 1px solid #10b981;
        border-radius: 8px;
        cursor: pointer;
      }
      .deliver:disabled {
        opacity: 0.5;
      }
    `,
  ],
})
export class WaiterOrdersPage {
  private readonly service = inject(WaiterOrderPointService);
  private readonly i18n = inject(I18nService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly t = this.i18n.t;

  readonly orders = signal<WaiterActiveOrder[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly busy = signal<Set<string>>(new Set());
  // the tab lives in the URL (?tab=) so a refresh stays on the same status
  readonly tab = signal<Tab>(
    this.route.snapshot.queryParamMap.get('tab') === 'ORDERED' ? 'ORDERED' : 'READY',
  );
  readonly tabOptions = computed(() => [
    { id: 'READY', name: this.t('orders.ready') },
    { id: 'ORDERED', name: this.t('orders.ordered') },
  ]);

  constructor() {
    this.load();
  }

  /** Switch tab → fetch that status from the backend. */
  setTab(tab: Tab): void {
    if (this.tab() === tab) return;
    this.tab.set(tab);
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
    this.load();
  }

  time(iso: string): string {
    return roTime(iso);
  }

  deliver(o: WaiterActiveOrder): void {
    if (o.status !== 'READY' || this.busy().has(o.id)) return;
    this.busy.update((s) => new Set(s).add(o.id));
    const prev = this.orders();
    this.orders.set(prev.filter((x) => x.id !== o.id)); // optimistic
    this.service.setOrderStatus(o.id, 'DELIVERED').subscribe({
      next: () => this.clearBusy(o.id),
      error: () => {
        this.orders.set(prev);
        this.clearBusy(o.id);
        this.error.set(this.t('orders.updateFailed'));
      },
    });
  }

  private clearBusy(id: string): void {
    this.busy.update((s) => {
      const next = new Set(s);
      next.delete(id);
      return next;
    });
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.myOrders(this.tab()).subscribe({
      next: (orders) => {
        // the API answers newest first; the board reads better oldest first
        this.orders.set([...orders].sort((a, b) => a.createdAt.localeCompare(b.createdAt)));
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(this.t('orders.loadFailed'));
      },
    });
  }
}
