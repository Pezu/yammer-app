import { Component, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { WaiterOrderPointService, WaiterPayment } from '../tables/waiter-order-point.service';
import { I18nService } from '../../../core/i18n.service';
import { ToastService } from '../../../core/toast.service';
import { WaiterRefreshService } from '../waiter-refresh.service';
import { ComboBox } from '../../../shared/combo-box';

type StatusFilter = 'ALL' | 'SUCCESS' | 'PENDING' | 'FAILED' | 'UNKNOWN';

/**
 * /waiter/payments — what was collected at the waiter's own tables during the location's OPEN
 * service day (protocol/PO settlements excluded), with the fiscal state of every receipt: a FAILED one can be re-issued from here,
 * an UNKNOWN one only after a manager resolves it in the backoffice.
 */
@Component({
  selector: 'app-waiter-payments-page',
  imports: [DecimalPipe, ComboBox],
  template: `
    <header class="page-head">
      <div class="view-combo">
        <app-combo-box [options]="filterOptions()" [value]="filter()" (valueChange)="select($any($event))" />
      </div>
    </header>

    <section class="page-body">
      @if (loading()) {
        <p class="state">{{ t('common.loading') }}</p>
      } @else if (error()) {
        <p class="state err">{{ error() }}</p>
      } @else if (visibleRows().length === 0) {
        <p class="state">{{ t('payments.empty') }}</p>
      } @else {
        <table>
          <thead>
            <tr>
              <th class="tcol">{{ t('payments.table') }}</th>
              <th class="ccol">{{ t('payments.type') }}</th>
              <th class="acol">{{ t('payments.amount') }}</th>
              <th class="acol">{{ t('payments.tip') }}</th>
              <th class="scol">{{ t('payments.status') }}</th>
            </tr>
          </thead>
          <tbody>
            @for (p of visibleRows(); track p.id) {
              <tr>
                <td class="tcol">{{ p.orderPointName }}</td>
                <td class="ccol">
                  @if (isCash(p)) {
                    <svg class="mic" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" [attr.aria-label]="t('payments.cash')"><circle cx="8" cy="8" r="6"></circle><path d="M18.09 10.37A6 6 0 1 1 10.34 18"></path><path d="M7 6h1v4"></path><path d="m16.71 13.88.7.71-2.82 2.82"></path></svg>
                  } @else if (isCard(p)) {
                    <svg class="mic" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" [attr.aria-label]="t('payments.card')"><rect x="2" y="5" width="20" height="14" rx="2"></rect><line x1="2" y1="10" x2="22" y2="10"></line></svg>
                  } @else {
                    <span class="mtype">{{ p.paymentType }}</span>
                  }
                </td>
                <td class="num acol">{{ p.amount | number: '1.2-2' }}</td>
                <td class="num acol">{{ p.tip | number: '1.2-2' }}</td>
                <td class="scol">
                  <span
                    class="dot"
                    [class.success]="p.fiscalStatus === 'SUCCESS'"
                    [class.pending]="p.fiscalStatus === 'PENDING'"
                    [class.failed]="p.fiscalStatus === 'FAILED'"
                    [class.unknown]="p.fiscalStatus === 'UNKNOWN'"
                    [attr.title]="p.fiscalStatus"
                  ></span>
                </td>
              </tr>
              @if (p.fiscalStatus === 'FAILED') {
                <tr class="fail-note">
                  <td colspan="5">
                    <div class="fail-row">
                      <span class="fail-msg">{{ t('payments.failedNote') }}</span>
                      <button type="button" class="retry-btn" [disabled]="retrying().has(p.id)" (click)="retry(p)">
                        {{ retrying().has(p.id) ? t('payments.retrying') : t('payments.retry') }}
                      </button>
                    </div>
                  </td>
                </tr>
              }
              @if (p.fiscalStatus === 'UNKNOWN') {
                <tr class="fail-note">
                  <td colspan="5">
                    <div class="fail-row">
                      <span class="fail-msg">{{ t('payments.unknownNote') }}</span>
                    </div>
                  </td>
                </tr>
              }
            }
          </tbody>
        </table>
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
        padding: 0 0.25rem 1.5rem;
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
      table {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.78rem;
        table-layout: fixed;
      }
      th,
      td {
        padding: 0.5rem 0.35rem;
        text-align: left;
        border-bottom: 1px solid var(--border);
        overflow: hidden;
        text-overflow: ellipsis;
      }
      th {
        font-size: 0.6rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.02em;
        color: var(--muted);
      }
      td.num {
        text-align: center;
        font-variant-numeric: tabular-nums;
      }
      .tcol {
        width: 3.5rem;
        font-weight: 700;
      }
      .acol {
        width: 4.2rem;
        text-align: center;
      }
      .ccol {
        width: 2.4rem;
        text-align: center;
      }
      th.acol,
      th.ccol,
      th.scol {
        text-align: center;
      }
      .scol {
        width: 4.5rem;
        text-align: center;
      }
      .mic {
        vertical-align: middle;
        color: var(--muted);
      }
      .mtype {
        font-size: 0.62rem;
        font-weight: 700;
        color: var(--muted);
      }
      .dot {
        display: inline-block;
        width: 10px;
        height: 10px;
        border-radius: 50%;
        background: var(--muted);
        vertical-align: middle;
      }
      .dot.success {
        background: #16a34a;
      }
      .dot.pending {
        background: #b88207;
      }
      .dot.failed {
        background: var(--danger);
      }
      .dot.unknown {
        background: #f59e0b;
      }
      .fail-note td {
        background: rgba(234, 77, 77, 0.1);
      }
      .fail-row {
        display: flex;
        align-items: center;
        gap: 0.6rem;
        padding: 0.1rem 0;
      }
      .fail-msg {
        flex: 1;
        font-size: 0.72rem;
        color: var(--danger);
        line-height: 1.25;
      }
      .retry-btn {
        flex: none;
        padding: 0.3rem 0.7rem;
        font: inherit;
        font-size: 0.72rem;
        font-weight: 600;
        color: var(--danger);
        background: #fff;
        border: 1px solid var(--danger);
        border-radius: 6px;
        cursor: pointer;
      }
      .retry-btn:disabled {
        opacity: 0.55;
        cursor: default;
      }
    `,
  ],
})
export class WaiterPaymentsPage {
  private readonly service = inject(WaiterOrderPointService);
  private readonly i18n = inject(I18nService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly refresh = inject(WaiterRefreshService);
  readonly t = this.i18n.t;

  readonly rows = signal<WaiterPayment[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly retrying = signal<Set<string>>(new Set());

  // the filter lives in the URL (?status=) so a refresh keeps it
  readonly filter = signal<StatusFilter>(this.readFilter());
  readonly filterOptions = computed(() => [
    { id: 'ALL', name: this.t('payments.all') },
    { id: 'SUCCESS', name: this.t('payments.success') },
    { id: 'PENDING', name: this.t('payments.pending') },
    { id: 'FAILED', name: this.t('payments.failed') },
    { id: 'UNKNOWN', name: this.t('payments.unknown') },
  ]);
  readonly visibleRows = computed(() =>
    this.filter() === 'ALL' ? this.rows() : this.rows().filter((p) => p.fiscalStatus === this.filter()),
  );

  constructor() {
    this.load();
    this.refresh.requested.pipe(takeUntilDestroyed()).subscribe(() => this.load());
  }

  /** CASH / CARD drive the row icon; any other payment type shows its name instead. */
  isCash(p: WaiterPayment): boolean {
    return (p.paymentType ?? '').toUpperCase() === 'CASH';
  }

  isCard(p: WaiterPayment): boolean {
    return (p.paymentType ?? '').toUpperCase() === 'CARD';
  }

  select(value: StatusFilter): void {
    this.filter.set(value);
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { status: value },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  private readFilter(): StatusFilter {
    const s = this.route.snapshot.queryParamMap.get('status');
    return s === 'SUCCESS' || s === 'PENDING' || s === 'FAILED' || s === 'UNKNOWN' ? s : 'ALL';
  }

  retry(p: WaiterPayment): void {
    if (this.retrying().has(p.id)) return;
    this.retrying.update((s) => new Set(s).add(p.id));
    this.service.retryFiscal(p.id).subscribe({
      // fiscalization runs async on the bridge; give it a moment, then refresh so the
      // new status (SUCCESS / still FAILED) shows without a manual reload
      next: () =>
        setTimeout(() => {
          this.retrying.update((s) => this.without(s, p.id));
          this.load();
        }, 2500),
      error: () => {
        this.retrying.update((s) => this.without(s, p.id));
        this.toast.show(this.t('payments.retryFailed'));
      },
    });
  }

  private without(s: Set<string>, id: string): Set<string> {
    const next = new Set(s);
    next.delete(id);
    return next;
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.myPayments().subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(this.t('payments.loadFailed'));
        this.loading.set(false);
      },
    });
  }
}
