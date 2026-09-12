import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

/** One node of the menu tree. Category: orderable=false. Product: orderable=true + price. */
export interface MenuNode {
  id: string;
  name: string;
  orderable: boolean;
  price: number | null;
  vatTypeId: string | null;
  children: MenuNode[];
}

export interface MenuOption {
  id: string;
  name: string;
}

export interface ProductOption {
  id: string;
  name: string;
  price: number | null;
  menuId: string;
  menuName: string;
}

export interface OrderPointMenu {
  orderPointId: string;
  orderPointName: string;
  menuId: string | null;
  items: MenuNode[];
  /** Every menu of the order point's location (for the menu switcher). */
  menus: MenuOption[];
  /** Every orderable product across the location's menus (for the search box). */
  products: ProductOption[];
}

export interface OrderItemInput {
  menuItemId: string | null;
  name: string;
  price: number;
  quantity: number;
}

export interface WaiterOrder {
  id: string;
  orderNo: number;
  orderPointId: string;
  orderPointName: string;
  createdBy: string | null;
  createdAt: string;
  status: string;
  items: { id: string; menuItemId: string | null; name: string; price: number | null; quantity: number }[];
  total: number;
}

export interface BillLine {
  menuItemId: string | null;
  name: string;
  price: number | null;
  quantity: number;
  paid: boolean;
  /** Set only on a partially-paid unit's remainder: the unit's original price. */
  originalPrice: number | null;
}

export type PaymentMode = 'FULL' | 'PARTIAL' | 'AMOUNT';

export interface PayInput {
  paymentTypeId: string;
  mode: PaymentMode;
  tip: number;
  /** AMOUNT mode: the fixed sum (capped server-side at the unpaid total). */
  amount?: number;
  /** PARTIAL mode: the quantities to settle; price targets that exact unit price
   *  (so a split remainder is paid separately from the product's regular lines). */
  items?: { menuItemId: string; quantity: number; price: number }[];
}

export interface OrderPointBill {
  orderPointName: string;
  /** Payment types accepted at this order point — the pay sheet offers exactly these. */
  paymentTypeIds: string[];
  /** Whether a table session is running; Close table is offered when open + fully paid. */
  sessionOpen: boolean;
  /** Customer self-ordering at this table: ALLOW / CONFIRM (waiter approves) / DISALLOW. */
  selfOrderMode: 'ALLOW' | 'CONFIRM' | 'DISALLOW';
  lines: BillLine[];
  total: number;
  unpaidTotal: number;
}

@Injectable({ providedIn: 'root' })
export class WaiterOrderPointService {
  private readonly http = inject(HttpClient);

  /** The order point plus its (default) menu tree, for the ordering screen. */
  menu(orderPointId: string): Observable<OrderPointMenu> {
    return this.http.get<OrderPointMenu>(`${environment.apiUrl}/order-points/${orderPointId}/menu`);
  }

  /** One menu's item tree (used when switching menus). */
  menuTree(menuId: string): Observable<MenuNode[]> {
    return this.http.get<MenuNode[]>(`${environment.apiUrl}/menu/menus/${menuId}/tree`);
  }

  /** Place an order at the point. */
  placeOrder(orderPointId: string, items: OrderItemInput[]): Observable<WaiterOrder> {
    return this.http.post<WaiterOrder>(`${environment.apiUrl}/orders`, { orderPointId, items });
  }

  /** Orders placed at the point, newest first. */
  orders(orderPointId: string): Observable<WaiterOrder[]> {
    const params = new HttpParams().set('orderPointId', orderPointId);
    return this.http.get<WaiterOrder[]>(`${environment.apiUrl}/orders`, { params });
  }

  /** Set the table's self-order mode (assigned users only). */
  setSelfOrderMode(orderPointId: string, mode: string): Observable<void> {
    return this.http.put<void>(
      `${environment.apiUrl}/order-points/${orderPointId}/self-order-mode`,
      { mode },
    );
  }

  /** The table's combined bill — every order's lines aggregated per product. */
  bill(orderPointId: string): Observable<OrderPointBill> {
    const params = new HttpParams().set('orderPointId', orderPointId);
    return this.http.get<OrderPointBill>(`${environment.apiUrl}/orders/bill`, { params });
  }

  /**
   * A payment against the point's bill; resolves to the fresh bill.
   * FULL settles everything; AMOUNT allocates a fixed sum (oldest lines first,
   * the last unit possibly partially paid); PARTIAL settles selected quantities.
   */
  pay(orderPointId: string, payment: PayInput): Observable<OrderPointBill> {
    return this.http.post<OrderPointBill>(`${environment.apiUrl}/orders/pay`, {
      orderPointId,
      ...payment,
    });
  }

  /** Close the table's session (only when fully settled) and free the table. */
  /** Print the unpaid bill as a proforma on the table's thermal printer (best-effort). */
  printProforma(orderPointId: string): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/order-points/${orderPointId}/proforma`, {});
  }

  closeTable(orderPointId: string): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/order-points/${orderPointId}/close`, {});
  }
}
