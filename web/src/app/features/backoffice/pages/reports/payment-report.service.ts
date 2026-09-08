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
  /** NONE / PENDING / SUCCESS / FAILED / UNKNOWN (fiscal receipt on the on-prem bridge). */
  fiscalStatus: FiscalStatus;
  receiptNumber: string | null;
}

export type FiscalStatus = 'NONE' | 'PENDING' | 'SUCCESS' | 'FAILED' | 'UNKNOWN';

@Injectable({ providedIn: 'root' })
export class PaymentReportService {
  private readonly http = inject(HttpClient);

  list(locationId: string): Observable<PaymentReportRow[]> {
    const params = new HttpParams().set('locationId', locationId);
    return this.http.get<PaymentReportRow[]>(`${environment.apiUrl}/payments`, { params });
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
