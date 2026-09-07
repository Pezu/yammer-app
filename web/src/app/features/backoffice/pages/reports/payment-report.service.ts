import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

/** One row of the payments report. */
export interface PaymentReportRow {
  id: string;
  orderPointName: string;
  waiter: string;
  amount: number;
  tip: number;
  total: number;
  paymentType: string;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class PaymentReportService {
  private readonly http = inject(HttpClient);

  list(locationId: string): Observable<PaymentReportRow[]> {
    const params = new HttpParams().set('locationId', locationId);
    return this.http.get<PaymentReportRow[]>(`${environment.apiUrl}/payments`, { params });
  }
}
