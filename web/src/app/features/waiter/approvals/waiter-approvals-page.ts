import { Component, OnDestroy, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Approvals, ApprovalService } from './approval.service';
import { timeAgo } from '../../../shared/relative-time';
import { I18nService } from '../../../core/i18n.service';

const POLL_MS = 10000;

/**
 * The waiter's Approvals page (hamburger → Approvals): customer devices asking to
 * join one of my tables, and customer orders awaiting confirmation on tables in
 * CONFIRM self-order mode. Approving an order sends it into the normal flow
 * (kanban + bill); denying deletes it.
 */
@Component({
  selector: 'app-waiter-approvals-page',
  imports: [DecimalPipe],
  template: `
    <div class="head">
      <h2>{{ t('approvals.title') }}</h2>
    </div>

    @if (error()) {
      <p class="state err">{{ error() }}</p>
    } @else if (loading()) {
      <p class="state">{{ t('common.loading') }}</p>
    } @else {
      <h3 class="sec">{{ t('approvals.customers') }}</h3>
      @if (data().customers.length === 0) {
        <p class="empty">{{ t('approvals.noCustomers') }}</p>
      } @else {
        <ul class="cards">
          @for (c of data().customers; track c.id) {
            <li class="card">
              <div class="card-main">
                <span class="table">{{ c.orderPointName }}</span>
                <span class="sub">{{ t('approvals.wantsToOrder') }} @if (c.requestedAt) {· {{ time(c.requestedAt) }}}</span>
              </div>
              <div class="acts">
                <button type="button" class="deny" [disabled]="busy()" (click)="decideCustomer(c.id, false)">{{ t('approvals.deny') }}</button>
                <button type="button" class="ok" [disabled]="busy()" (click)="decideCustomer(c.id, true)">{{ t('approvals.approve') }}</button>
              </div>
            </li>
          }
        </ul>
      }

      <h3 class="sec">{{ t('approvals.orders') }}</h3>
      @if (data().orders.length === 0) {
        <p class="empty">{{ t('approvals.noOrders') }}</p>
      } @else {
        <ul class="cards">
          @for (o of data().orders; track o.id) {
            <li class="card order">
              <div class="card-main">
                <span class="table">{{ o.orderPointName }} · #{{ o.orderNo }} <span class="sub">{{ time(o.createdAt) }}</span></span>
                <ul class="items">
                  @for (it of o.items; track it.id) {
                    <li>
                      <span class="qty">{{ it.quantity }}×</span>
                      <span class="name" [innerHTML]="it.name"></span>
                      <span class="price">{{ (it.price ?? 0) * it.quantity | number: '1.2-2' }}</span>
                    </li>
                  }
                </ul>
                <div class="total"><span>{{ t('approvals.total') }}</span><span>{{ o.total | number: '1.2-2' }} RON</span></div>
              </div>
              <div class="acts">
                <button type="button" class="deny" [disabled]="busy()" (click)="decideOrder(o.id, false)">{{ t('approvals.deny') }}</button>
                <button type="button" class="ok" [disabled]="busy()" (click)="decideOrder(o.id, true)">{{ t('approvals.approve') }}</button>
              </div>
            </li>
          }
        </ul>
      }
    }
  `,
  styles: `
    :host {
      display: block;
      padding: 1rem;
      max-width: 34rem;
      margin: 0 auto;
      width: 100%;
    }
    .head h2 {
      margin: 0 0 0.5rem;
      font-size: 1.15rem;
      color: var(--text);
    }
    .state {
      margin: 2rem 0;
      color: var(--muted);
      text-align: center;
    }
    .state.err {
      color: var(--danger);
    }
    .sec {
      margin: 1.25rem 0 0.5rem;
      font-size: 0.8rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: var(--muted);
    }
    .empty {
      margin: 0.25rem 0 0.75rem;
      font-size: 0.85rem;
      color: var(--muted);
    }
    .cards {
      margin: 0;
      padding: 0;
      list-style: none;
      display: flex;
      flex-direction: column;
      gap: 0.6rem;
    }
    .card {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem;
      padding: 0.75rem 0.9rem;
      background: #fff;
      border: 1px solid var(--border);
      border-radius: 10px;
    }
    .card.order {
      align-items: flex-start;
    }
    .card-main {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }
    .table {
      font-weight: 700;
      color: var(--text);
    }
    .sub {
      font-size: 0.78rem;
      font-weight: 400;
      color: var(--muted);
    }
    .items {
      margin: 0.25rem 0 0;
      padding: 0;
      list-style: none;
      display: flex;
      flex-direction: column;
      gap: 0.2rem;
    }
    .items li {
      display: flex;
      gap: 0.5rem;
      font-size: 0.82rem;
      color: var(--text);
    }
    .items .qty {
      flex: none;
      font-weight: 700;
    }
    .items .name {
      flex: 1;
      min-width: 0;
    }
    .items .price {
      font-variant-numeric: tabular-nums;
      color: var(--muted);
    }
    .total {
      display: flex;
      justify-content: space-between;
      margin-top: 0.35rem;
      padding-top: 0.35rem;
      border-top: 1px solid var(--border);
      font-size: 0.85rem;
      font-weight: 700;
      color: var(--text);
      font-variant-numeric: tabular-nums;
    }
    .acts {
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
      flex: none;
    }
    .acts button {
      padding: 0.45rem 0.9rem;
      font: inherit;
      font-size: 0.82rem;
      font-weight: 700;
      border-radius: 8px;
      cursor: pointer;
    }
    .acts .ok {
      color: #fff;
      background: var(--primary);
      border: 1px solid var(--primary);
    }
    .acts .deny {
      color: var(--danger);
      background: #fff;
      border: 1px solid rgba(220, 53, 69, 0.4);
    }
    .acts button:disabled {
      opacity: 0.6;
      cursor: default;
    }
  `,
})
export class WaiterApprovalsPage implements OnDestroy {
  private readonly service = inject(ApprovalService);
  readonly t = inject(I18nService).t;

  readonly data = signal<Approvals>({ customers: [], orders: [] });
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);

  private poll: ReturnType<typeof setInterval> | undefined;

  constructor() {
    this.load(true);
    this.poll = setInterval(() => this.load(false), POLL_MS);
  }

  ngOnDestroy(): void {
    if (this.poll) clearInterval(this.poll);
  }

  time(iso: string): string {
    return timeAgo(iso, this.t);
  }

  decideCustomer(id: string, approve: boolean): void {
    this.busy.set(true);
    this.service.decideCustomer(id, approve).subscribe({
      next: () => {
        this.busy.set(false);
        this.load(false);
      },
      error: () => {
        this.busy.set(false);
        this.error.set(this.t('approvals.saveFailed'));
      },
    });
  }

  decideOrder(id: string, approve: boolean): void {
    this.busy.set(true);
    this.service.decideOrder(id, approve).subscribe({
      next: () => {
        this.busy.set(false);
        this.load(false);
      },
      error: () => {
        this.busy.set(false);
        this.error.set(this.t('approvals.saveFailed'));
      },
    });
  }

  private load(initial: boolean): void {
    if (initial) this.loading.set(true);
    this.service.list().subscribe({
      next: (data) => {
        this.data.set(data);
        this.loading.set(false);
        this.error.set(null);
      },
      error: () => {
        this.loading.set(false);
        if (initial) this.error.set(this.t('approvals.loadFailed'));
      },
    });
  }
}
