import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';

/**
 * A frame the order-point QR sheets are printed on: a background image plus where the QR
 * code and the order point's name sit on it. Positions/sizes are fractions of the image
 * (x and sizes of its width, y of its height).
 */
export interface QrTemplate {
  id: string;
  name: string;
  hasImage: boolean;
  qrX: number;
  qrY: number;
  qrSize: number;
  labelY: number;
  labelSize: number;
  labelColor: string;
}

/** Editable fields; geometry omitted on create = server defaults. */
export interface QrTemplateInput {
  name: string;
  qrX?: number;
  qrY?: number;
  qrSize?: number;
  labelY?: number;
  labelSize?: number;
  labelColor?: string;
}

@Injectable({ providedIn: 'root' })
export class QrTemplateService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/qr-templates`;

  list(): Observable<QrTemplate[]> {
    return this.http.get<QrTemplate[]>(this.baseUrl);
  }

  create(template: QrTemplateInput): Observable<QrTemplate> {
    return this.http.post<QrTemplate>(this.baseUrl, template);
  }

  update(id: string, template: QrTemplateInput): Observable<QrTemplate> {
    return this.http.put<QrTemplate>(`${this.baseUrl}/${id}`, template);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  uploadImage(id: string, file: File): Observable<QrTemplate> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<QrTemplate>(`${this.baseUrl}/${id}/image`, form);
  }

  deleteImage(id: string): Observable<QrTemplate> {
    return this.http.delete<QrTemplate>(`${this.baseUrl}/${id}/image`);
  }

  /** Public URL of a template's frame image (cache-busted by `version`). */
  imageUrl(id: string, version: number): string {
    return `${this.baseUrl}/${id}/image?v=${version}`;
  }
}
