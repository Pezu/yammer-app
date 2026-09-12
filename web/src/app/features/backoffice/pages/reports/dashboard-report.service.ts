import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface DashboardSummary {
  ordered: number;
  paid: number;
  tips: number;
  remaining: number;
  orders: number;
  payments: number;
  averageOrder: number;
  /** ordered on protocol tables (points accepting the PROTOCOL payment type) */
  orderedProtocol: number;
  /** settled with the PROTOCOL payment type (comped) */
  paidProtocol: number;
}

export interface DashboardBucket {
  /** yyyy-MM-ddTHH:mm, server-local */
  at: string;
  ordered: number;
  paid: number;
  orders: number;
}

export interface DashboardTableRow {
  table: string;
  protocol: boolean;
  ordered: number;
  paidCash: number;
  paidCard: number;
  paidProtocol: number;
  paidOther: number;
  tips: number;
  remaining: number;
}

export interface DashboardProductRow {
  product: string;
  quantity: number;
  sales: number;
}

export interface DashboardWaiterRow {
  waiter: string;
  orders: number;
  sales: number;
  paidCash: number;
  paidCard: number;
  paidProtocol: number;
  paidOther: number;
  tipsCash: number;
  tipsCard: number;
  unsettled: number;
}

export interface DashboardPaymentTypeRow {
  type: string;
  count: number;
  amount: number;
  tips: number;
}

export interface FinalReportRow {
  waiter: string;
  paidCard: number;
  paidCash: number;
  tipCard: number;
  tipCash: number;
  total: number;
}

export interface Dashboard {
  summary: DashboardSummary;
  series: DashboardBucket[];
  bucketMinutes: number;
  tables: DashboardTableRow[];
  products: DashboardProductRow[];
  waiters: DashboardWaiterRow[];
  paymentTypes: DashboardPaymentTypeRow[];
  finalReport: FinalReportRow[];
}

@Injectable({ providedIn: 'root' })
export class DashboardReportService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/reports/dashboard`;

  /** One location, inclusive date range (yyyy-MM-dd). */
  load(locationId: string, from: string, to: string): Observable<Dashboard> {
    const params = new HttpParams().set('locationId', locationId).set('from', from).set('to', to);
    return this.http.get<Dashboard>(this.baseUrl, { params });
  }

  /** Print the final report (one slip per waiter) on one of the location's thermal printers. */
  printFinal(locationId: string, from: string, to: string, printerId: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/final/print`, { locationId, from, to, printerId });
  }
}
