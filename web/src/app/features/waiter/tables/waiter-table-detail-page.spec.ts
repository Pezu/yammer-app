import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { WaiterTableDetailPage } from './waiter-table-detail-page';
import { OrderPointBill, PayInput } from './waiter-order-point.service';
import { WaiterOrderPointService } from './waiter-order-point.service';
import { PaymentTypeService } from '../../backoffice/pages/payment-types/payment-type.service';

const billOf = (unpaidTotal: number, paymentTypeIds: string[] = ['pt-cash']): OrderPointBill => ({
  orderPointName: 'T12',
  paymentTypeIds,
  inService: true,
  keepOpen: true,
  discountPercent: null,
  selfOrderMode: 'ALLOW',
  lines: [
    { menuItemId: 'm1', name: 'Pizza', price: 42, quantity: 2, paid: false, originalPrice: null },
  ],
  total: unpaidTotal,
  unpaidTotal,
});

function setup(bill: OrderPointBill, payFails = false) {
  const submitted: PayInput[] = [];
  const service = {
    bill: (): Observable<OrderPointBill> => of(bill),
    pay: (_id: string, payload: PayInput): Observable<OrderPointBill> => {
      submitted.push(payload);
      return payFails ? throwError(() => ({ status: 409 })) : of(bill);
    },
  };
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: WaiterOrderPointService, useValue: service },
      {
        provide: PaymentTypeService,
        useValue: { list: () => of(bill.paymentTypeIds.map((id) => ({ id, type: 'CASH' }))) },
      },
    ],
  });
  const fixture = TestBed.createComponent(WaiterTableDetailPage);
  fixture.detectChanges();
  return { page: fixture.componentInstance, submitted };
}

describe('WaiterTableDetailPage — pay sheet', () => {
  it('sends the amount the sheet displays, never the typed one (UX-W14)', () => {
    const { page, submitted } = setup(billOf(50));
    page.openPay();
    page.setMode('AMOUNT');
    page.setFixedAmount({ value: '500' } as HTMLInputElement);

    expect(page.amountCapped()).toBe(true);
    expect(page.fixedAmount()).toBe(50);
    expect(page.grossTotal()).toBe(50);

    page.pay('pt-cash');
    expect(submitted).toEqual([{ paymentTypeId: 'pt-cash', mode: 'AMOUNT', tip: 0, amount: 50 }]);
  });

  it('keeps a typed amount below the bill untouched', () => {
    const { page, submitted } = setup(billOf(50));
    page.openPay();
    page.setMode('AMOUNT');
    page.setFixedAmount({ value: '20' } as HTMLInputElement);

    expect(page.amountCapped()).toBe(false);
    page.pay('pt-cash');
    expect(submitted[0].amount).toBe(20);
  });

  it('a refused payment keeps the sheet, the selection and the tip (UX-C3)', () => {
    const { page } = setup(billOf(84), true);
    page.openPay();
    page.setMode('PARTIAL');
    page.moveToPaying(page.unpaidChips()[0].key);
    page.setTip('p10');
    const selection = page.paySelection();

    page.pay('pt-cash');

    expect(page.payOpen()).toBe(true);
    expect(page.payError()).toBeTruthy();
    expect(page.paySelection()).toEqual(selection);
    expect(page.tipMode()).toBe('p10');
    expect(page.error()).toBeNull();
  });

  it('offers Pay even when the order point has no payment type (UX-W22)', () => {
    const { page } = setup(billOf(120, []));
    expect(page.canPay()).toBe(true);
    expect(page.payOptions()).toEqual([]);
  });
});
