import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export type SelfOrderMode = 'ALLOW' | 'CONFIRM' | 'DISALLOW';

export interface OrderPoint {
  id: string;
  locationId: string;
  name: string;
  /** Free label shown under the name on the waiter's tiles. */
  nickname: string | null;
  selfOrderMode: SelfOrderMode;
  typeId: string;
  selfPayTypeId: string | null;
  allowMultipleUsers: boolean;
  /** Runs a tab (pay later); false = pay as you order. */
  keepOpen: boolean;
  paymentTypeIds: string[];
  menuId: string | null;
  serviceOrderPointId: string | null;
  printerId: string | null;
  cashRegisterId: string | null;
}

export interface OrderPointInput {
  name: string;
  nickname: string | null;
  selfOrderMode: SelfOrderMode | null;
  selfPayTypeId: string | null;
  allowMultipleUsers: boolean;
  /** Runs a tab (pay later); false = pay as you order. */
  keepOpen: boolean;
  paymentTypeIds: string[];
  menuId: string | null;
  serviceOrderPointId: string | null;
  printerId: string | null;
  cashRegisterId: string | null;
}

export interface CreateOrderPointsBatch {
  locationId: string;
  typeId: string;
  count: number;
  selfPayTypeId: string | null;
  allowMultipleUsers: boolean;
  /** Runs a tab (pay later); false = pay as you order. */
  keepOpen: boolean;
  paymentTypeIds: string[];
  menuId: string | null;
  serviceOrderPointId: string | null;
  printerId: string | null;
  cashRegisterId: string | null;
}

/** One row of the assignment board: an order point plus who currently works it. */
export interface AssignableOrderPoint {
  id: string;
  name: string;
  typeId: string;
  allowMultipleUsers: boolean;
  /** Runs a tab (pay later); false = pay as you order. */
  keepOpen: boolean;
  assignedCount: number;
  assignedToMe: boolean;
  assignedNames: string[];
}

@Injectable({ providedIn: 'root' })
export class OrderPointService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/order-points`;

  list(locationId: string): Observable<OrderPoint[]> {
    const params = new HttpParams().set('locationId', locationId);
    return this.http.get<OrderPoint[]>(this.baseUrl, { params });
  }

  createBatch(batch: CreateOrderPointsBatch): Observable<OrderPoint[]> {
    return this.http.post<OrderPoint[]>(`${this.baseUrl}/batch`, batch);
  }

  update(id: string, orderPoint: OrderPointInput): Observable<OrderPoint> {
    return this.http.put<OrderPoint>(`${this.baseUrl}/${id}`, orderPoint);
  }

  /** Order points assigned to the current user. */
  assigned(): Observable<OrderPoint[]> {
    return this.http.get<OrderPoint[]>(`${this.baseUrl}/assigned`);
  }

  /** The location's points with their current assignment state (the picker board). */
  assignable(locationId: string): Observable<AssignableOrderPoint[]> {
    const params = new HttpParams().set('locationId', locationId);
    return this.http.get<AssignableOrderPoint[]>(`${this.baseUrl}/assignable`, { params });
  }

  assign(id: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/assign`, {});
  }

  unassign(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}/assign`);
  }

  /** Split a table into its next free slot (T12.1 → T12.2); resolves to the new point. */
  split(id: string): Observable<OrderPoint> {
    return this.http.post<OrderPoint>(`${this.baseUrl}/${id}/split`, {});
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
