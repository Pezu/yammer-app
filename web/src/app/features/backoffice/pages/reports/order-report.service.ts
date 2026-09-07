import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface PagedResponse<T> {
  content: T[];
  total: number;
  page: number;
  size: number;
}

export interface OrderItem {
  id: string;
  menuItemId: string | null;
  name: string;
  price: number | null;
  quantity: number;
  paid: boolean;
}

export interface Order {
  id: string;
  orderNo: number;
  orderPointId: string;
  orderPointName: string;
  createdBy: string | null;
  createdAt: string;
  status: string;
  items: OrderItem[];
  total: number;
  paid: 'NOT' | 'PAR' | 'PAID';
}

export interface OrderPointOption {
  id: string;
  name: string;
}

export interface WaiterOption {
  /** The filter value (raw created_by). */
  username: string;
  /** The display label. */
  name: string;
}

export interface OrderFilterOptions {
  orderPoints: OrderPointOption[];
  waiters: WaiterOption[];
}

/** Optional server-side filters for the paginated orders list. */
export interface OrderFilters {
  orderNo?: number | null;
  orderPointId?: string | null;
  waiter?: string | null;
  paid?: string | null;
}

@Injectable({ providedIn: 'root' })
export class OrderReportService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/orders`;

  /** One page of the location's orders (newest first), paginated and filtered server-side. */
  listPaged(
    locationId: string,
    page: number,
    size: number,
    filters: OrderFilters = {},
  ): Observable<PagedResponse<Order>> {
    let params = new HttpParams().set('locationId', locationId).set('page', page).set('size', size);
    if (filters.orderNo != null) params = params.set('orderNo', filters.orderNo);
    if (filters.orderPointId) params = params.set('orderPointId', filters.orderPointId);
    if (filters.waiter) params = params.set('waiter', filters.waiter);
    if (filters.paid) params = params.set('paid', filters.paid);
    return this.http.get<PagedResponse<Order>>(`${this.baseUrl}/page`, { params });
  }

  /** Order-point and waiter options for the orders-report filter combos. */
  filterOptions(locationId: string): Observable<OrderFilterOptions> {
    const params = new HttpParams().set('locationId', locationId);
    return this.http.get<OrderFilterOptions>(`${this.baseUrl}/filter-options`, { params });
  }

  /** Update an order's unpaid item quantities (quantity ≤ 0 deletes the item). */
  updateItems(orderId: string, items: { id: string; quantity: number }[]): Observable<Order> {
    return this.http.patch<Order>(`${this.baseUrl}/${orderId}/items`, { items });
  }

  /** Delete an order entirely (only allowed when nothing is paid). */
  deleteOrder(orderId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${orderId}`);
  }
}
