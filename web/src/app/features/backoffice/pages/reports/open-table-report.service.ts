import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

/** One open table session: who opened it, when, and the outstanding amount. */
export interface OpenTableReportRow {
  /** The table itself — a sitting has no id of its own now. */
  orderPointId: string;
  orderPointName: string;
  /** Who works the table (comma-separated when several). */
  openedBy: string;
  /** When the round started: the earliest active assignment. */
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
