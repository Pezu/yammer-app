import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import {
  OrderPointBill,
  PayInput,
  PaymentMode,
  WaiterOrderPointService,
} from './waiter-order-point.service';
import {
  PaymentType,
  PaymentTypeService,
} from '../../backoffice/pages/payment-types/payment-type.service';
import { ToastService } from '../../../core/toast.service';
import { ComboBox } from '../../../shared/combo-box';

type TipMode = 'none' | 'p10' | 'p12' | 'p15' | 'customPct' | 'customAmt';

const round2 = (n: number): number => Math.round(n * 100) / 100;

/** One pickable chip: an unpaid bill line (a split remainder is its own chip). */
interface ChipLine {
  key: string;
  menuItemId: string;
  name: string;
  price: number;
  qty: number;
  /** A partially-paid unit's remainder (highlighted orange, like on the bill). */
  partial: boolean;
}

/**
 * One assigned table: the COMBINED bill (all orders aggregated per product), placing a
 * new order, and paying — full bill, a fixed sum, or old yammer's partial picker (drag
 * items from Bill to "Paying", tap to move one, long-press to type a quantity). Tips
 * follow the old pay modal: preset percents + custom % / custom RON. The payment-type
 * buttons (the point's configured types) submit directly.
 */
@Component({
  selector: 'app-waiter-table-detail-page',
  imports: [DecimalPipe, ComboBox],
  templateUrl: './waiter-table-detail-page.html',
  styleUrl: './waiter-table-detail-page.scss',
})
export class WaiterTableDetailPage {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(WaiterOrderPointService);
  private readonly paymentTypeService = inject(PaymentTypeService);
  private readonly toast = inject(ToastService);

  readonly id = this.route.snapshot.paramMap.get('id') ?? '';
  /** Table name from navigation state; replaced by the API's name once the bill loads. */
  readonly name = signal<string>((history.state?.name as string) ?? '');

  readonly bill = signal<OrderPointBill | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  private readonly paymentTypes = signal<PaymentType[]>([]);
  private readonly paymentTypeById = computed(
    () => new Map(this.paymentTypes().map((t) => [t.id, t.type])),
  );

  /** The pay sheet's method buttons — exactly the types configured on this order point. */
  readonly payOptions = computed(() =>
    (this.bill()?.paymentTypeIds ?? [])
      .map((id) => ({ id, name: this.paymentTypeById().get(id) ?? '?' })),
  );

  readonly canPay = computed(
    () => (this.bill()?.unpaidTotal ?? 0) > 0 && this.payOptions().length > 0,
  );

  /** Everything settled → the table can be closed (the ONLY way a session ends). */
  readonly closing = signal(false);
  readonly canClose = computed(
    () =>
      !this.loading() &&
      !this.error() &&
      (this.bill()?.sessionOpen ?? false) &&
      this.bill()!.lines.every((l) => l.paid),
  );

  /** Close the session and free the table, then return to the tables list. */
  closeTable(): void {
    if (this.closing()) {
      return;
    }
    this.closing.set(true);
    this.service.closeTable(this.id).subscribe({
      next: () => {
        this.toast.show(`${this.name()} closed`);
        this.router.navigate(['/waiter/tables']);
      },
      error: () => {
        this.closing.set(false);
        this.toast.show('Could not close the table.');
      },
    });
  }

  constructor() {
    this.paymentTypeService.list().subscribe({
      next: (types) => this.paymentTypes.set(types),
    });
    this.service.bill(this.id).subscribe({
      next: (bill) => {
        this.bill.set(bill);
        this.name.set(bill.orderPointName);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load the table.');
        this.loading.set(false);
      },
    });
  }

  newOrder(): void {
    this.router.navigate(['/waiter/tables', this.id, 'order'], {
      state: { name: this.name() },
    });
  }

  back(): void {
    this.router.navigate(['/waiter/tables']);
  }

  // --- pay sheet ---

  readonly payOpen = signal(false);
  readonly submitting = signal(false);
  readonly payMode = signal<PaymentMode>('FULL');
  /** AMOUNT mode: the fixed sum typed by the waiter. */
  readonly fixedAmount = signal<number | null>(null);

  readonly unpaidLines = computed(() => this.bill()?.lines.filter((l) => !l.paid) ?? []);
  readonly paidLines = computed(() => this.bill()?.lines.filter((l) => l.paid) ?? []);
  readonly paidTotal = computed(() =>
    round2(this.paidLines().reduce((sum, l) => sum + (l.price ?? 0) * l.quantity, 0)),
  );

  /** Which products to show: the outstanding ones (default) or the already-paid ones. */
  readonly view = signal<'unpaid' | 'paid'>('unpaid');
  readonly viewOptions = [
    { id: 'unpaid', name: 'Unpaid' },
    { id: 'paid', name: 'Paid' },
  ];

  /** Customer self-ordering at this table — set by the assigned waiter. */
  readonly selfOrderModeOptions = [
    { id: 'ALLOW', name: 'Self order' },
    { id: 'CONFIRM', name: 'Self order + confirm' },
    { id: 'DISALLOW', name: 'No self order' },
  ];

  setSelfOrderMode(mode: string): void {
    const bill = this.bill();
    if (!bill || bill.selfOrderMode === mode) return;
    const previous = bill.selfOrderMode;
    this.bill.set({ ...bill, selfOrderMode: mode as OrderPointBill['selfOrderMode'] });
    this.service.setSelfOrderMode(this.id, mode).subscribe({
      error: () => {
        const current = this.bill();
        if (current) this.bill.set({ ...current, selfOrderMode: previous });
        this.toast.show('Could not change the self-order mode');
      },
    });
  }
  readonly shownLines = computed(() =>
    this.view() === 'unpaid' ? this.unpaidLines() : this.paidLines(),
  );

  /** The partial picker's inventory — one chip per unpaid bill line (bill lines are
   *  already aggregated by product + unit price, so remainders stay separate). */
  readonly unpaidChips = computed<ChipLine[]>(() =>
    this.unpaidLines()
      .filter((l) => !!l.menuItemId)
      .map((l) => ({
        key: `${l.menuItemId}|${l.price ?? 0}|${l.originalPrice ?? ''}`,
        menuItemId: l.menuItemId!,
        name: l.name,
        price: l.price ?? 0,
        qty: l.quantity,
        partial: l.originalPrice != null,
      })),
  );

  /** chip key -> quantity selected to pay (partial mode). */
  readonly paySelection = signal<Record<string, number>>({});
  readonly dragged = signal<{ id: string; source: 'bill' | 'paying' } | null>(null);

  /** Product whose quantity editor (opened by long-press) is shown, or null. */
  readonly editingId = signal<string | null>(null);
  readonly editingName = computed(() => {
    const id = this.editingId();
    return id ? (this.unpaidChips().find((p) => p.key === id)?.name ?? '') : '';
  });
  readonly editingMax = computed(() => {
    const id = this.editingId();
    return id ? (this.unpaidChips().find((p) => p.key === id)?.qty ?? 0) : 0;
  });
  readonly editingCurrent = computed(() => {
    const id = this.editingId();
    return id ? (this.paySelection()[id] ?? 0) : 0;
  });

  readonly billList = computed(() =>
    this.unpaidChips()
      .map((p) => ({ ...p, rem: p.qty - (this.paySelection()[p.key] ?? 0) }))
      .filter((p) => p.rem > 0),
  );
  readonly payingList = computed(() =>
    this.unpaidChips()
      .map((p) => ({ ...p, pay: this.paySelection()[p.key] ?? 0 }))
      .filter((p) => p.pay > 0),
  );

  readonly payingTotal = computed(() =>
    round2(this.payingList().reduce((sum, p) => sum + p.price * p.pay, 0)),
  );

  /** What this payment charges (before tip), per mode. */
  readonly chargeTotal = computed(() => {
    const due = this.bill()?.unpaidTotal ?? 0;
    switch (this.payMode()) {
      case 'FULL':
        return due;
      case 'AMOUNT':
        return round2(Math.min(this.fixedAmount() ?? 0, due));
      case 'PARTIAL':
        return this.payingTotal();
    }
  });

  // --- tip (as in the old pay modal: presets + custom % / custom RON) ---

  readonly tipMode = signal<TipMode>('none');
  readonly tipCustomPercent = signal<number | null>(null);
  readonly tipCustomAmount = signal<number | null>(null);
  readonly computedTip = computed(() => {
    const base = this.chargeTotal() || 0;
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
        if (p != null && !isNaN(p)) {
          tip = base * (p / 100);
        }
        break;
      }
      case 'customAmt': {
        const a = this.tipCustomAmount();
        if (a != null && !isNaN(a)) {
          tip = a;
        }
        break;
      }
    }
    return Math.max(0, round2(tip));
  });
  readonly totalToPay = computed(() => round2(this.chargeTotal() + this.computedTip()));

  openPay(): void {
    if (!this.canPay()) {
      return;
    }
    this.payMode.set('FULL');
    this.fixedAmount.set(null);
    this.paySelection.set({});
    this.tipMode.set('none');
    this.tipCustomPercent.set(null);
    this.tipCustomAmount.set(null);
    this.error.set(null);
    this.payOpen.set(true);
  }

  closePay(): void {
    this.payOpen.set(false);
  }

  setMode(mode: PaymentMode): void {
    this.payMode.set(mode);
  }

  setTip(mode: TipMode): void {
    this.tipMode.set(mode);
  }

  setFixedAmount(value: string): void {
    this.fixedAmount.set(value === '' ? null : +value);
  }

  // --- drag/tap move between Bill and Paying ---

  moveToPaying(id: string): void {
    const product = this.unpaidChips().find((p) => p.key === id);
    if (!product) {
      return;
    }
    const cur = this.paySelection()[id] ?? 0;
    if (cur >= product.qty) {
      return;
    }
    this.paySelection.update((m) => ({ ...m, [id]: cur + 1 }));
  }

  moveToBill(id: string): void {
    const cur = this.paySelection()[id] ?? 0;
    if (cur <= 0) {
      return;
    }
    this.paySelection.update((m) => {
      const next = { ...m };
      if (cur - 1 <= 0) {
        delete next[id];
      } else {
        next[id] = cur - 1;
      }
      return next;
    });
  }

  /** Select the whole bill for payment (move every unpaid item to "Paying"). */
  moveAllToPaying(): void {
    const all: Record<string, number> = {};
    for (const p of this.unpaidChips()) {
      all[p.key] = p.qty;
    }
    this.paySelection.set(all);
  }

  /** Move everything back to the bill. */
  clearPaying(): void {
    this.paySelection.set({});
  }

  // long-press (any pointer): open a quantity editor for the pressed product
  private pressTimer: ReturnType<typeof setTimeout> | null = null;
  private clearPress(): void {
    if (this.pressTimer) {
      clearTimeout(this.pressTimer);
      this.pressTimer = null;
    }
  }

  openQtyEditor(id: string): void {
    this.editingId.set(id);
  }

  applyQty(value: number): void {
    const id = this.editingId();
    if (id) {
      this.setPayQty(id, value);
    }
    this.editingId.set(null);
  }

  cancelQtyEdit(): void {
    this.editingId.set(null);
  }

  /** Set an exact quantity to pay for a product, clamped to what's still unpaid. */
  setPayQty(id: string, value: number): void {
    const product = this.unpaidChips().find((p) => p.key === id);
    if (!product) {
      return;
    }
    let q = Math.floor(value);
    if (isNaN(q) || q < 0) {
      q = 0;
    }
    if (q > product.qty) {
      q = product.qty;
    }
    this.paySelection.update((m) => {
      const next = { ...m };
      if (q <= 0) {
        delete next[id];
      } else {
        next[id] = q;
      }
      return next;
    });
  }

  // mouse: native HTML5 drag-and-drop
  onDragStart(id: string, source: 'bill' | 'paying'): void {
    this.clearPress(); // a real drag started — not a long-press
    this.dragged.set({ id, source });
  }

  dropToPaying(): void {
    const d = this.dragged();
    if (d?.source === 'bill') {
      this.moveToPaying(d.id);
    }
    this.dragged.set(null);
  }

  dropToBill(): void {
    const d = this.dragged();
    if (d?.source === 'paying') {
      this.moveToBill(d.id);
    }
    this.dragged.set(null);
  }

  // tap (mouse + touch): move one unit; suppressed right after a real touch-drag
  private justDragged = false;
  onChipClick(id: string, source: 'bill' | 'paying'): void {
    if (this.justDragged) {
      this.justDragged = false;
      return;
    }
    if (source === 'bill') {
      this.moveToPaying(id);
    } else {
      this.moveToBill(id);
    }
  }

  // touch/pen: a pointer-drag shim with a floating ghost (HTML5 DnD doesn't fire on touch)
  private touchDrag: {
    id: string;
    source: 'bill' | 'paying';
    startX: number;
    startY: number;
    moved: boolean;
    ghost?: HTMLElement;
  } | null = null;

  onChipPointerDown(event: PointerEvent, id: string, source: 'bill' | 'paying'): void {
    // long-press → quantity editor (works for both mouse hold and touch hold)
    this.clearPress();
    this.pressTimer = setTimeout(() => {
      this.pressTimer = null;
      if (this.touchDrag) {
        this.touchDrag.ghost?.remove();
        this.touchDrag = null;
      }
      this.justDragged = true; // suppress the click/drag that follows the release
      setTimeout(() => (this.justDragged = false), 400);
      this.openQtyEditor(id);
    }, 500);

    if (event.pointerType === 'mouse') {
      return; // mouse uses native DnD + click (plus the long-press timer above)
    }
    this.touchDrag = { id, source, startX: event.clientX, startY: event.clientY, moved: false };
  }

  @HostListener('document:pointermove', ['$event'])
  onDocPointerMove(event: PointerEvent): void {
    const d = this.touchDrag;
    if (!d) {
      return;
    }
    if (!d.moved && Math.hypot(event.clientX - d.startX, event.clientY - d.startY) < 8) {
      return;
    }
    if (!d.moved) {
      d.moved = true;
      this.clearPress(); // moved → it's a drag, not a long-press
      d.ghost = this.makeGhost(d.id);
    }
    event.preventDefault();
    if (d.ghost) {
      d.ghost.style.left = `${event.clientX}px`;
      d.ghost.style.top = `${event.clientY}px`;
    }
  }

  @HostListener('document:pointerup', ['$event'])
  onDocPointerUp(event: PointerEvent): void {
    this.clearPress(); // released before the long-press fired → cancel it
    const d = this.touchDrag;
    if (!d) {
      return;
    }
    this.touchDrag = null;
    d.ghost?.remove();
    if (!d.moved) {
      return; // it was a tap — let the click handler move it
    }
    this.justDragged = true; // suppress the synthetic click that follows
    setTimeout(() => (this.justDragged = false), 400);
    const zone = (document.elementFromPoint(event.clientX, event.clientY) as HTMLElement | null)
      ?.closest('[data-zone]')
      ?.getAttribute('data-zone');
    if (zone === 'paying' && d.source === 'bill') {
      this.moveToPaying(d.id);
    } else if (zone === 'bill' && d.source === 'paying') {
      this.moveToBill(d.id);
    }
  }

  private makeGhost(id: string): HTMLElement {
    const product = this.unpaidChips().find((p) => p.key === id);
    const el = document.createElement('div');
    el.textContent = this.plainText(product?.name ?? '');
    el.style.cssText =
      'position:fixed;transform:translate(-50%,-50%);z-index:9999;pointer-events:none;' +
      'padding:0.4rem 0.75rem;border-radius:999px;background:#3454d1;color:#fff;' +
      "font:700 0.85rem system-ui,sans-serif;box-shadow:0 0.5rem 1rem rgba(18,27,46,0.3);";
    document.body.appendChild(el);
    return el;
  }

  private plainText(html: string): string {
    const tmp = document.createElement('div');
    tmp.innerHTML = html;
    return tmp.textContent ?? '';
  }

  // --- submit: the method buttons pay directly (as in the old pay modal) ---

  readonly canSubmit = computed(() => {
    if (this.submitting()) {
      return false;
    }
    switch (this.payMode()) {
      case 'FULL':
        return (this.bill()?.unpaidTotal ?? 0) > 0;
      case 'AMOUNT':
        return (this.fixedAmount() ?? 0) > 0;
      case 'PARTIAL':
        return this.payingList().length > 0;
    }
  });

  pay(paymentTypeId: string): void {
    if (!this.canSubmit()) {
      return;
    }
    const mode = this.payMode();
    const payload: PayInput = { paymentTypeId, mode, tip: this.computedTip() };
    if (mode === 'AMOUNT') {
      payload.amount = this.fixedAmount() ?? 0;
    }
    if (mode === 'PARTIAL') {
      payload.items = this.payingList().map((p) => ({
        menuItemId: p.menuItemId,
        quantity: p.pay,
        price: p.price,
      }));
    }
    this.submitting.set(true);
    this.service.pay(this.id, payload).subscribe({
      next: (bill) => {
        this.bill.set(bill);
        this.submitting.set(false);
        this.payOpen.set(false);
        // card payments stay PENDING until the softPOS terminal confirms
        this.toast.show(
          this.paymentTypeById().get(paymentTypeId) === 'CARD'
            ? 'Sent to the card terminal — awaiting confirmation'
            : 'Payment recorded',
        );
      },
      error: () => {
        this.submitting.set(false);
        this.error.set('Could not record the payment. Please try again.');
        this.payOpen.set(false);
      },
    });
  }
}
