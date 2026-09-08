import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

/** MOBILE = a bridge phone; registers and printers attach to one. */
export type IntegrationType = 'CASH_REGISTER' | 'PRINTER' | 'MOBILE';
/** How a register / printer is reached: over the LAN, or through a MOBILE bridge. */
export type ConnectionType = 'MOBILE' | 'TCP';

export interface Integration {
  id: string;
  locationId: string;
  name: string;
  ip: string | null;
  type: IntegrationType;
  connection: ConnectionType;
  /** MOBILE rows: the bridge phone's device id. */
  deviceId: string | null;
  /** Registers / printers with connection MOBILE: the MOBILE row they attach to. */
  bridgeId: string | null;
  bridgeName: string | null;
  /** MOBILE rows: whether the phone currently holds a live bridge session. */
  online: boolean | null;
}

export interface IntegrationInput {
  locationId: string;
  name: string;
  ip: string | null;
  type: IntegrationType;
  connection: ConnectionType;
  deviceId: string | null;
  bridgeId: string | null;
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
