import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';
import { FinalReportRow } from '../reports/dashboard-report.service';

/** One service day of a location. `open` false means it is already cashed up. */
export interface Shift {
  id: string;
  locationId: string;
  /** The manager's own label for the day; optional. */
  nickname: string | null;
  openedBy: string | null;
  openedAt: string;
  closedBy: string | null;
  closedAt: string | null;
  open: boolean;
}

/** What closing a day returns: the cash-up per waiter and whether the slips printed. */
export interface ShiftClose {
  shift: Shift;
  rows: FinalReportRow[];
  printed: boolean;
  printError: string | null;
}

@Injectable({ providedIn: 'root' })
export class ShiftService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/shifts`;

  /** The location's service days, newest first. */
  list(locationId: string): Observable<Shift[]> {
    const params = new HttpParams().set('locationId', locationId);
    return this.http.get<Shift[]>(this.baseUrl, { params });
  }

  /** Tables that still have an open session — while any exist, the day cannot be closed. */
  openTables(locationId: string): Observable<string[]> {
    const params = new HttpParams().set('locationId', locationId);
    return this.http.get<string[]>(`${this.baseUrl}/open-tables`, { params });
  }

  open(locationId: string, nickname?: string): Observable<Shift> {
    return this.http.post<Shift>(`${this.baseUrl}/open`, {
      locationId,
      nickname: nickname?.trim() || null,
    });
  }

  /** Close the day; with a printerId the cash-up slips also go to that thermal printer. */
  close(locationId: string, printerId?: string): Observable<ShiftClose> {
    return this.http.post<ShiftClose>(`${this.baseUrl}/close`, {
      locationId,
      printerId: printerId || null,
    });
  }

  rename(id: string, nickname: string): Observable<Shift> {
    return this.http.put<Shift>(`${this.baseUrl}/${id}`, { nickname: nickname.trim() || null });
  }
}
