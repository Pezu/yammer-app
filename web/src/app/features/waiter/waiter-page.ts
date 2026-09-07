import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { ToastService } from '../../core/toast.service';
import { WaiterMenuCacheService } from './waiter-menu-cache.service';
import { AppLogo } from '../../shared/logo.component';
import { I18nService, LANG_OPTIONS, isLang } from '../../core/i18n.service';
import { ComboBox } from '../../shared/combo-box';

/** Waiter shell — topbar with the hamburger menu; pages render in the outlet below. */
@Component({
  selector: 'app-waiter-page',
  imports: [AppLogo, RouterOutlet, ComboBox],
  template: `
    <header class="topbar">
      <app-logo [height]="30" />
      <div class="user">
        <span class="uname">{{ displayName() }}</span>
        <button type="button" class="burger" (click)="toggleMenu()" aria-label="Menu">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="3" y1="6" x2="21" y2="6"></line><line x1="3" y1="12" x2="21" y2="12"></line><line x1="3" y1="18" x2="21" y2="18"></line></svg>
        </button>

        @if (menuOpen()) {
          <div class="menu-backdrop" (click)="closeMenu()"></div>
          <div class="menu-panel">
            <!-- more items land above; Logout stays last -->
            <button type="button" class="menu-item" (click)="goToTables()">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"></rect><rect x="14" y="3" width="7" height="7"></rect><rect x="14" y="14" width="7" height="7"></rect><rect x="3" y="14" width="7" height="7"></rect></svg>
              <span>{{ t('menu.tables') }}</span>
            </button>
            <button type="button" class="menu-item" (click)="goToApprovals()">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 11l3 3L22 4"></path><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"></path></svg>
              <span>{{ t('menu.approvals') }}</span>
            </button>
            <button type="button" class="menu-item" (click)="refreshMenu()">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"></polyline><polyline points="1 20 1 14 7 14"></polyline><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path></svg>
              <span>{{ t('menu.refreshMenu') }}</span>
            </button>
            <div class="menu-item lang" role="group" [attr.aria-label]="t('common.language')">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="2" y1="12" x2="22" y2="12"></line><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"></path></svg>
              <span class="lang-combo">
                <app-combo-box [options]="langOptions" [value]="i18n.lang()" (valueChange)="setLang($event)" />
              </span>
            </div>
            <button type="button" class="menu-item logout" (click)="logout()">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path><polyline points="16 17 21 12 16 7"></polyline><line x1="21" y1="12" x2="9" y2="12"></line></svg>
              <span>{{ t('menu.logout') }}</span>
            </button>
          </div>
        }
      </div>
    </header>
    <main class="content">
      <router-outlet></router-outlet>
    </main>

    @if (toast.message(); as msg) {
      <div class="toast">{{ msg }}</div>
    }
  `,
  styles: [
    `
      :host {
        display: flex;
        flex-direction: column;
        min-height: 100vh;
        min-height: 100dvh;
      }
      .topbar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        height: 56px;
        padding: 0 1rem;
        background: #fff;
        border-bottom: 1px solid var(--border);
      }
      .user {
        position: relative;
        display: inline-flex;
        align-items: center;
        gap: 0.75rem;
        min-width: 0;
      }
      .uname {
        max-width: 40vw;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: 0.85rem;
        font-weight: 600;
        color: var(--text);
      }
      .burger {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 36px;
        height: 36px;
        padding: 0;
        color: var(--text);
        background: none;
        border: none;
        border-radius: 8px;
        cursor: pointer;
      }
      .burger:hover {
        background: var(--page-bg);
      }
      .menu-backdrop {
        position: fixed;
        inset: 0;
        z-index: 20;
      }
      .menu-panel {
        position: absolute;
        top: calc(100% + 8px);
        right: 0;
        z-index: 21;
        min-width: 200px;
        padding: 0.25rem;
        background: #fff;
        border: 1px solid var(--border);
        border-radius: 10px;
        box-shadow: 0 0.5rem 1.5rem rgba(18, 27, 46, 0.15);
      }
      .menu-item {
        display: flex;
        align-items: center;
        gap: 0.6rem;
        width: 100%;
        padding: 0.7rem 0.9rem;
        font: inherit;
        font-size: 0.9rem;
        font-weight: 600;
        color: var(--text);
        background: none;
        border: none;
        border-radius: 8px;
        cursor: pointer;
      }
      .menu-item:hover {
        background: var(--page-bg);
      }
      .menu-item.logout:hover {
        color: var(--danger);
      }
      .menu-item.lang {
        cursor: default;
      }
      .menu-item.lang:hover {
        background: none;
      }
      .lang-combo {
        flex: 1;
        min-width: 0;
      }
      .content {
        flex: 1;
        background: var(--page-bg);
      }
      .toast {
        position: fixed;
        bottom: 1.25rem;
        left: 50%;
        transform: translateX(-50%);
        z-index: 60;
        padding: 0.6rem 1.1rem;
        font-size: 0.85rem;
        font-weight: 600;
        color: #fff;
        background: rgba(18, 27, 46, 0.9);
        border-radius: 999px;
        box-shadow: 0 0.5rem 1.5rem rgba(18, 27, 46, 0.3);
      }
    `,
  ],
})
export class WaiterPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly menuCache = inject(WaiterMenuCacheService);
  readonly toast = inject(ToastService);
  readonly i18n = inject(I18nService);
  readonly t = this.i18n.t;
  readonly langOptions = LANG_OPTIONS;

  constructor() {
    // waiters default to Romanian; a change is remembered on this device
    this.i18n.init('waiter');
  }

  setLang(lang: string): void {
    if (isLang(lang)) this.i18n.setLang(lang);
  }

  /** The user's display name; falls back to the username for accounts without one. */
  readonly displayName = computed(() => {
    const session = this.auth.session();
    return session?.name || session?.username || '';
  });

  /** Hamburger menu in the top bar — Logout is (and stays) the last item. */
  readonly menuOpen = signal(false);

  toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  closeMenu(): void {
    this.menuOpen.set(false);
  }

  goToTables(): void {
    this.menuOpen.set(false);
    this.router.navigateByUrl('/waiter/tables');
  }

  goToApprovals(): void {
    this.menuOpen.set(false);
    this.router.navigateByUrl('/waiter/approvals');
  }

  /** Drop the cached menus so the next (or an open) order page loads them fresh. */
  refreshMenu(): void {
    this.menuOpen.set(false);
    this.menuCache.refresh();
    this.toast.show(this.t('menu.menuRefreshed'));
  }

  logout(): void {
    this.menuOpen.set(false);
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
