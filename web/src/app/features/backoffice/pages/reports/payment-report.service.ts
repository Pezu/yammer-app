import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

/** One row of the payments report. */
export interface PaymentPage {
  content: PaymentReportRow[];
  total: number;
  page: number;
  size: number;
  totals: { amount: number; tip: number; total: number };
}

export interface PaymentReportRow {
  id: string;
  orderPointName: string;
  waiter: string;
  amount: number;
  tip: number;
  total: number;
  paymentType: string;
  createdAt: string;
  /** NONE / PENDING / SUCCESS / FAILED / UNKNOWN (fiscal receipt on the on-prem bridge). */
  fiscalStatus: FiscalStatus;
  receiptNumber: string | null;
}

export type FiscalStatus = 'NONE' | 'PENDING' | 'SUCCESS' | 'FAILED' | 'UNKNOWN';

@Injectable({ providedIn: 'root' })
export class PaymentReportService {
  private readonly http = inject(HttpClient);

  /** One page (0-based) of the location's payments, newest first, with totals over all of them. */
  page(locationId: string, page: number, size: number): Observable<PaymentPage> {
    const params = new HttpParams().set('locationId', locationId).set('page', page).set('size', size);
    return this.http.get<PaymentPage>(`${environment.apiUrl}/payments`, { params });
  }

  /** Re-issue a FAILED fiscal receipt (the bridge de-dupes by payment id). */
  retryFiscal(paymentId: string): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/payments/${paymentId}/retry-fiscal`, {});
  }

  /** Operator verdict on an UNKNOWN receipt after checking the register. */
  resolveUnknownFiscal(paymentId: string, printed: boolean, receiptNumber: string | null): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/payments/${paymentId}/resolve-unknown`, {
      printed,
      receiptNumber,
    });
  }
}
