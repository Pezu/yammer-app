import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface NotPaidLine {
  name: string;
  quantity: number;
  price: number;
  total: number;
}

/** One settlement closed as PROTOCOL or PO — consumption that brought no money — with its product lines. */
export interface NotPaidReportRow {
  paymentId: string;
  orderPointName: string;
  waiter: string;
  at: string | null;
  paymentType: string;
  amount: number;
  items: NotPaidLine[];
}

@Injectable({ providedIn: 'root' })
export class NotPaidReportService {
  private readonly http = inject(HttpClient);

  /** Inclusive date range (yyyy-MM-dd). */
  list(locationId: string, from: string, to: string): Observable<NotPaidReportRow[]> {
    const params = new HttpParams().set('locationId', locationId).set('from', from).set('to', to);
    return this.http.get<NotPaidReportRow[]>(`${environment.apiUrl}/reports/not-paid`, { params });
  }
}
