import { Component, OnDestroy, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CustomerOrderPointService, OnlinePaymentStatus } from './customer-order-point.service';

/**
 * Where the Netopia gateway sends the customer's browser back
 * (`/customer/order-point/:id/payment-return?ref=`). The browser return proves
 * nothing — the page polls the intent status until the server-to-server IPN
 * lands (PAID → the order exists) or the payment fails/expires.
 */
@Component({
  selector: 'app-payment-return-page',
  imports: [RouterLink],
  template: `
    <main class="ret">
      @if (status() === 'PAID') {
        <div class="icon ok">✓</div>
        <h1>Payment confirmed</h1>
        <p>Your order has been sent. Thank you!</p>
      } @else if (status() === 'FAILED' || status() === 'EXPIRED') {
        <div class="icon bad">✕</div>
        <h1>Payment not completed</h1>
        <p>Your card was not charged for this order. You can try again from the menu.</p>
      } @else if (error()) {
        <div class="icon bad">✕</div>
        <h1>Something went wrong</h1>
        <p>We could not check your payment. Please ask your waiter.</p>
      } @else {
        <div class="spinner"></div>
        <h1>Checking your payment…</h1>
        <p>This usually takes a few seconds.</p>
      }
      <a class="back" [routerLink]="['/customer/order-point', opId]">Back to the menu</a>
    </main>
  `,
  styles: `
    :host {
      display: block;
      min-height: 100vh;
      background: #fff;
    }
    .ret {
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      padding: 2rem;
      text-align: center;
    }
    .icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 64px;
      height: 64px;
      font-size: 2rem;
      font-weight: 700;
      color: #fff;
      border-radius: 50%;
    }
    .icon.ok {
      background: #28a745;
    }
    .icon.bad {
      background: var(--danger);
    }
    h1 {
      margin: 0.5rem 0 0;
      font-size: 1.4rem;
      color: var(--text);
    }
    p {
      margin: 0;
      color: var(--muted);
    }
    .spinner {
      width: 40px;
      height: 40px;
      border: 4px solid var(--border);
      border-top-color: var(--primary);
      border-radius: 50%;
      animation: ret-spin 0.9s linear infinite;
    }
    @keyframes ret-spin {
      to {
        transform: rotate(360deg);
      }
    }
    .back {
      margin-top: 1.25rem;
      padding: 0.7rem 1.5rem;
      font-weight: 700;
      color: var(--primary);
      text-decoration: none;
      border: 1px solid var(--primary);
      border-radius: 8px;
    }
  `,
})
export class PaymentReturnPage implements OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(CustomerOrderPointService);

  readonly opId = this.route.snapshot.paramMap.get('id') ?? '';
  private readonly ref = this.route.snapshot.queryParamMap.get('ref') ?? '';

  readonly status = signal<OnlinePaymentStatus['status'] | null>(null);
  readonly error = signal(false);

  private poll: ReturnType<typeof setInterval> | undefined;
  private attempts = 0;

  constructor() {
    if (!this.ref) {
      this.error.set(true);
      return;
    }
    this.check();
    this.poll = setInterval(() => this.check(), 3000);
  }

  ngOnDestroy(): void {
    if (this.poll) clearInterval(this.poll);
  }

  private check(): void {
    // The IPN can lag the browser return; give it up to ~2 minutes.
    if (++this.attempts > 40) {
      this.error.set(true);
      if (this.poll) clearInterval(this.poll);
      return;
    }
    this.service.paymentStatus(this.ref).subscribe({
      next: (res) => {
        this.status.set(res.status);
        if (res.status !== 'PENDING' && this.poll) clearInterval(this.poll);
      },
      error: () => {
        /* transient — keep polling until the attempt cap */
      },
    });
  }
}
