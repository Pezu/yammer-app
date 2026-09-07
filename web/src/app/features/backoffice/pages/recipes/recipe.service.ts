import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

/** One row of a product's recipe: a component product + the fraction consumed. */
export interface RecipeComponent {
  id: string;
  componentProductId: string;
  componentName: string;
  quantity: number;
}

export interface RecipeComponentInput {
  componentProductId: string;
  quantity: number;
}

@Injectable({ providedIn: 'root' })
export class RecipeService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/recipes`;

  get(productId: string): Observable<RecipeComponent[]> {
    return this.http.get<RecipeComponent[]>(`${this.baseUrl}/${productId}`);
  }

  /** Replace the product's recipe with the given rows. */
  save(productId: string, rows: RecipeComponentInput[]): Observable<RecipeComponent[]> {
    return this.http.put<RecipeComponent[]>(`${this.baseUrl}/${productId}`, rows);
  }
}
