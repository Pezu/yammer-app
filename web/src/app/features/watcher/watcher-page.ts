import { Component, computed, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { AppLogo } from '../../shared/logo.component';
import { DashboardPage } from '../backoffice/pages/reports/dashboard-page';

/**
 * /watcher — the WATCHER role's whole UI: the backoffice dashboard alone (no sidebar, no
 * menus), read-only, refreshing itself every minute. Ported from the old app, where a
 * watcher saw the live dashboard of their own client and nothing else.
 */
@Component({
  selector: 'app-watcher-page',
  imports: [AppLogo, DashboardPage],
  template: `
    <header class="topbar">
      <app-logo [height]="30" />
      <div class="user">
        <span class="uname">{{ displayName() }}</span>
        <button type="button" class="logout" (click)="logout()">Logout</button>
      </div>
    </header>
    <main class="content">
      <app-dashboard-page [readOnly]="true" [refreshEverySeconds]="60" />
    </main>
  `,
  styles: [
    `
      :host {
        display: block;
        min-height: 100vh;
        min-height: 100dvh;
        background: var(--page-bg);
      }
      .topbar {
        position: sticky;
        top: 0;
        z-index: 10;
        display: flex;
        align-items: center;
        justify-content: space-between;
        height: 56px;
        padding: 0 1rem;
        background: #fff;
        border-bottom: 1px solid var(--border);
      }
      .user {
        display: flex;
        align-items: center;
        gap: 0.75rem;
      }
      .uname {
        font-size: 0.9rem;
        font-weight: 600;
        color: var(--text);
      }
      .logout {
        padding: 0.35rem 0.7rem;
        font: inherit;
        font-size: 0.8rem;
        font-weight: 600;
        color: var(--text);
        background: #fff;
        border: 1px solid var(--border);
        border-radius: 6px;
        cursor: pointer;
      }
    `,
  ],
})
export class WatcherPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly displayName = computed(() => {
    const session = this.auth.session();
    return session?.name || session?.username || '';
  });

  logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
