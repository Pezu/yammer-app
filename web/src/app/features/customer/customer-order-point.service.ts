import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

/** One node of the menu tree: a category (orderable=false, has children) or a product. */
export interface MenuNode {
  id: string;
  name: string;
  orderable: boolean;
  price: number | null;
  vatTypeId: string | null;
  imageObject: string | null;
  /** Catalog product description (size, ingredients); null for categories. */
  description: string | null;
  children: MenuNode[];
}

export type SelfOrderMode = 'ALLOW' | 'CONFIRM' | 'DISALLOW';
export type CustomerStatus = 'NONE' | 'PENDING' | 'APPROVED' | 'DENIED';

/** Public, customer-facing view of an order point (resolved from the QR's order-point id). */
export interface CustomerOrderPoint {
  id: string;
  name: string;
  clientId: string | null;
  /** A waiter has opened the table — ordering is only allowed while open. */
  sessionOpen: boolean;
  selfOrderMode: SelfOrderMode;
  /** This device's approval state at the CURRENT session (from the stored token). */
  customerStatus: CustomerStatus;
  /** ONLINE self-pay table — ordering redirects through the Netopia gateway. */
  selfPayOnline: boolean;
  menu: MenuNode[];
}

/**
 * Result of placing a customer order. Pay-later → `orderId`. ONLINE self-pay →
 * `paymentUrl` (redirect the browser there) + `reference` (polled by the return page).
 */
export interface PlaceOrderResult {
  orderId: string | null;
  paymentUrl: string | null;
  reference: string | null;
  /** The order is a DRAFT (table in CONFIRM mode) — the waiter must accept it before it counts. */
  pendingApproval: boolean;
}

/** Online-payment intent status, polled by the payment-return page. */
export interface OnlinePaymentStatus {
  status: 'PENDING' | 'PAID' | 'FAILED' | 'EXPIRED';
  orderId: string | null;
}

/** One aggregated bill line; `originalPrice` marks a split unit (partially paid). */
export interface CustomerBillLine {
  menuItemId: string | null;
  name: string;
  price: number | null;
  quantity: number;
  paid: boolean;
  originalPrice: number | null;
}

/** The table's current bill for an approved customer device. */
export interface CustomerBill {
  lines: CustomerBillLine[];
  total: number;
  unpaidTotal: number;
}

/** Result of joining a table: the token this browser stores + its approval status. */
export interface JoinResult {
  token: string;
  status: CustomerStatus;
}

@Injectable({ providedIn: 'root' })
export class CustomerOrderPointService {
  private readonly http = inject(HttpClient);

  /** Public endpoint — no auth; table + client + menu + open state + this device's approval. */
  getOrderPoint(opId: string, token?: string | null): Observable<CustomerOrderPoint> {
    return this.http.get<CustomerOrderPoint>(`${environment.apiUrl}/public/order-points/${opId}`, {
      params: token ? { token } : {},
    });
  }

  /** Join (or resume) the table's open session; 409 while the table is not open. */
  join(opId: string, token?: string | null): Observable<JoinResult> {
    return this.http.post<JoinResult>(`${environment.apiUrl}/public/order-points/${opId}/join`, {
      token: token ?? null,
    });
  }

  /**
   * Place a self-service order (requires the APPROVED token). Only menu-item ids +
   * quantities are sent (prices resolved server-side). 409 = table not open; 403 = not
   * approved / self-ordering disallowed.
   */
  placeOrder(
    opId: string,
    token: string,
    items: { menuItemId: string; quantity: number }[],
    returnUrl?: string,
  ): Observable<PlaceOrderResult> {
    return this.http.post<PlaceOrderResult>(
      `${environment.apiUrl}/public/order-points/${opId}/orders`,
      { token, items, returnUrl: returnUrl ?? null },
    );
  }

  /** The table's bill (unpaid + paid lines) — needs the APPROVED token (403 otherwise). */
  bill(opId: string, token: string): Observable<CustomerBill> {
    return this.http.get<CustomerBill>(`${environment.apiUrl}/public/order-points/${opId}/bill`, {
      params: { token },
    });
  }

  /** Poll the status of an online payment (after returning from the gateway). */
  paymentStatus(reference: string): Observable<OnlinePaymentStatus> {
    return this.http.get<OnlinePaymentStatus>(
      `${environment.apiUrl}/public/payments/${reference}/status`,
    );
  }

  /** Public URL serving a stored menu-item image. */
  imageUrl(object: string): string {
    return `${environment.apiUrl}/public/menu-image?object=${encodeURIComponent(object)}`;
  }

  /** Public URL serving the client's logo (the brand logo in the top bar). */
  clientLogoUrl(clientId: string): string {
    return `${environment.apiUrl}/clients/${clientId}/logo`;
  }
}
