import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface SelfPayType {
  id: string;
  type: string;
}

@Injectable({ providedIn: 'root' })
export class SelfPayTypeService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/self-pay-types`;

  list(): Observable<SelfPayType[]> {
    return this.http.get<SelfPayType[]>(this.baseUrl);
  }

  create(type: string): Observable<SelfPayType> {
    return this.http.post<SelfPayType>(this.baseUrl, { type });
  }

  update(id: string, type: string): Observable<SelfPayType> {
    return this.http.put<SelfPayType>(`${this.baseUrl}/${id}`, { type });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
