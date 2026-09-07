import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

/**
 * A catalog product of one location — the single source of truth menus point at.
 * name may carry rich HTML (render with [innerHTML]). Price lives on the MENU
 * entry, not here — the same product can cost differently per menu.
 */
export interface Product {
  id: string;
  locationId: string;
  name: string;
  description: string | null;
  vatTypeId: string | null;
  imageObject: string | null;
}

export interface ProductInput {
  locationId: string;
  name: string;
  description: string;
  vatTypeId: string | null;
  imageObject: string | null;
}

@Injectable({ providedIn: 'root' })
export class ProductService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/products`;

  list(locationId: string): Observable<Product[]> {
    const params = new HttpParams().set('locationId', locationId);
    return this.http.get<Product[]>(this.baseUrl, { params });
  }

  create(product: ProductInput): Observable<Product> {
    return this.http.post<Product>(this.baseUrl, product);
  }

  update(id: string, product: ProductInput): Observable<Product> {
    return this.http.put<Product>(`${this.baseUrl}/${id}`, product);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  /** Upload a product image; resolves to its object-storage key (same store as menu images). */
  uploadImage(file: File): Observable<{ object: string }> {
    const data = new FormData();
    data.append('file', file);
    return this.http.post<{ object: string }>(`${environment.apiUrl}/menu/image`, data);
  }

  /** Public URL serving a stored product image. */
  imageUrl(object: string): string {
    return `${environment.apiUrl}/public/menu-image?object=${encodeURIComponent(object)}`;
  }
}
