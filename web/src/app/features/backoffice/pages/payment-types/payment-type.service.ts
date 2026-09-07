import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface PaymentType {
  id: string;
  type: string;
}

@Injectable({ providedIn: 'root' })
export class PaymentTypeService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/payment-types`;

  list(): Observable<PaymentType[]> {
    return this.http.get<PaymentType[]>(this.baseUrl);
  }

  create(type: string): Observable<PaymentType> {
    return this.http.post<PaymentType>(this.baseUrl, { type });
  }

  update(id: string, type: string): Observable<PaymentType> {
    return this.http.put<PaymentType>(`${this.baseUrl}/${id}`, { type });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
