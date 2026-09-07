import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';

/** A customer device asking to join one of my tables. */
export interface CustomerApprovalRow {
  id: string;
  orderPointId: string;
  orderPointName: string;
  requestedAt: string | null;
}

/** A customer order awaiting confirmation (CONFIRM self-order mode). */
export interface ApprovalOrderRow {
  id: string;
  orderNo: number;
  orderPointId: string;
  orderPointName: string;
  createdAt: string;
  items: { id: string; name: string; price: number | null; quantity: number }[];
  total: number;
}

export interface Approvals {
  customers: CustomerApprovalRow[];
  orders: ApprovalOrderRow[];
}

@Injectable({ providedIn: 'root' })
export class ApprovalService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/approvals`;

  list(): Observable<Approvals> {
    return this.http.get<Approvals>(this.baseUrl);
  }

  decideCustomer(id: string, approve: boolean): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/customers/${id}`, { approve });
  }

  decideOrder(id: string, approve: boolean): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/orders/${id}`, { approve });
  }
}
