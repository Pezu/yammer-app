import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

/** One open table session: who opened it, when, and the outstanding amount. */
export interface OpenTableReportRow {
  sessionId: string;
  orderPointName: string;
  openedBy: string;
  openedAt: string | null;
  amount: number;
}

@Injectable({ providedIn: 'root' })
export class OpenTableReportService {
  private readonly http = inject(HttpClient);

  list(locationId: string): Observable<OpenTableReportRow[]> {
    const params = new HttpParams().set('locationId', locationId);
    return this.http.get<OpenTableReportRow[]>(`${environment.apiUrl}/reports/open-tables`, {
      params,
    });
  }
}
