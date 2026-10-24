import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';

/**
 * The top bar's refresh button. Pages re-fetch their own data instead of reloading
 * the document, which on venue wifi can fail to come back and takes a half-built
 * order with it.
 */
@Injectable({ providedIn: 'root' })
export class WaiterRefreshService {
  private readonly requests = new Subject<void>();
  readonly requested: Observable<void> = this.requests.asObservable();

  request(): void {
    this.requests.next();
  }
}
