import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export type IntegrationType = 'CASH_REGISTER' | 'PRINTER';
export type ConnectionType = 'USB' | 'TCP';

export interface Integration {
  id: string;
  locationId: string;
  name: string;
  ip: string | null;
  type: IntegrationType;
  connection: ConnectionType;
  deviceId: string | null;
}

export interface IntegrationInput {
  locationId: string;
  name: string;
  ip: string | null;
  type: IntegrationType;
  connection: ConnectionType;
  deviceId: string | null;
}

/** A bridge (desktop or phone) currently connected to the backend. */
export interface BridgeDevice {
  deviceId: string;
  deviceName: string;
  connectedAt: string;
}

@Injectable({ providedIn: 'root' })
export class IntegrationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/integrations`;

  list(locationId: string, type?: IntegrationType): Observable<Integration[]> {
    let params = new HttpParams().set('locationId', locationId);
    if (type) {
      params = params.set('type', type);
    }
    return this.http.get<Integration[]>(this.baseUrl, { params });
  }

  create(input: IntegrationInput): Observable<Integration> {
    return this.http.post<Integration>(this.baseUrl, input);
  }

  update(id: string, input: IntegrationInput): Observable<Integration> {
    return this.http.put<Integration>(`${this.baseUrl}/${id}`, input);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  /** Bridge devices currently connected — options for the USB device picker. */
  devices(): Observable<BridgeDevice[]> {
    return this.http.get<BridgeDevice[]>(`${environment.apiUrl}/bridge/devices`);
  }
}
