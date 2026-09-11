import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface Location {
  id: string;
  name: string;
  clientId: string;
  active: boolean;
  /** Frame the location's QR sheets are printed on (Catalog → QR Templates), or null for the catalog's first frame. */
  qrTemplateId: string | null;
}

export interface LocationInput {
  name: string;
  clientId: string | null;
  active: boolean;
  qrTemplateId: string | null;
}

/**
 * The location a backoffice page should pre-select: the single ACTIVE one when there is
 * exactly one, else the only location at all; undefined when the user has to choose.
 */
export function defaultLocation(locations: Location[]): Location | undefined {
  const active = locations.filter((l) => l.active);
  if (active.length === 1) {
    return active[0];
  }
  return locations.length === 1 ? locations[0] : undefined;
}

@Injectable({ providedIn: 'root' })
export class LocationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/locations`;

  list(clientId?: string): Observable<Location[]> {
    const params = clientId ? new HttpParams().set('clientId', clientId) : undefined;
    return this.http.get<Location[]>(this.baseUrl, { params });
  }

  create(location: LocationInput): Observable<Location> {
    return this.http.post<Location>(this.baseUrl, location);
  }

  update(id: string, location: LocationInput): Observable<Location> {
    return this.http.put<Location>(`${this.baseUrl}/${id}`, location);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  /** Printable PDF of customer-ordering QR codes — one per TABLE/BAR point of the location. */
  exportQrPdf(id: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${id}/qr`, { responseType: 'blob' });
  }
}
