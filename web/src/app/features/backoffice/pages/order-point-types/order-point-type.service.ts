import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

export interface OrderPointType {
  id: string;
  type: string;
}

@Injectable({ providedIn: 'root' })
export class OrderPointTypeService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/order-point-types`;

  list(): Observable<OrderPointType[]> {
    return this.http.get<OrderPointType[]>(this.baseUrl);
  }

  create(type: string): Observable<OrderPointType> {
    return this.http.post<OrderPointType>(this.baseUrl, { type });
  }

  update(id: string, type: string): Observable<OrderPointType> {
    return this.http.put<OrderPointType>(`${this.baseUrl}/${id}`, { type });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}
