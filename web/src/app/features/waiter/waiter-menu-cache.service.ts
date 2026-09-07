import { Injectable, inject, signal } from '@angular/core';
import { Observable, of, tap } from 'rxjs';
import {
  MenuNode,
  OrderPointMenu,
  WaiterOrderPointService,
} from './tables/waiter-order-point.service';

interface MenuCacheStore {
  menus: Record<string, OrderPointMenu>;
  trees: Record<string, MenuNode[]>;
}

const STORAGE_KEY = 'yammer.waiter.menu-cache';

/**
 * Frontend cache for the waiter's menus. A menu is fetched from the API the first
 * time it is needed, persisted to localStorage, and served from there afterwards —
 * it only changes when the waiter taps "Refresh menu" in the drawer (or logs out).
 */
@Injectable({ providedIn: 'root' })
export class WaiterMenuCacheService {
  private readonly api = inject(WaiterOrderPointService);
  private store: MenuCacheStore = this.read();

  /** Bumped by refresh(); an open order page reloads its menu when it changes. */
  readonly version = signal(0);

  /** The order point's menu — from the cache, or fetched once and cached. */
  menu(orderPointId: string): Observable<OrderPointMenu> {
    const cached = this.store.menus[orderPointId];
    if (cached) {
      return of(cached);
    }
    return this.api.menu(orderPointId).pipe(
      tap((m) => {
        this.store.menus[orderPointId] = m;
        // the response embeds the default menu's tree — seed it so switching
        // back to the default menu doesn't refetch
        if (m.menuId) {
          this.store.trees[m.menuId] = m.items;
        }
        this.write();
      }),
    );
  }

  /** A specific menu's item tree — from the cache, or fetched once and cached. */
  menuTree(menuId: string): Observable<MenuNode[]> {
    const cached = this.store.trees[menuId];
    if (cached) {
      return of(cached);
    }
    return this.api.menuTree(menuId).pipe(
      tap((tree) => {
        this.store.trees[menuId] = tree;
        this.write();
      }),
    );
  }

  /** Drop everything cached so menus load fresh from the API on next need. */
  refresh(): void {
    this.clear();
    this.version.update((v) => v + 1);
  }

  /** Wipe the cache without notifying open pages (used on logout). */
  clear(): void {
    this.store = { menus: {}, trees: {} };
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // storage unavailable — the in-memory store is already cleared
    }
  }

  private read(): MenuCacheStore {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw) as Partial<MenuCacheStore>;
        return { menus: parsed.menus ?? {}, trees: parsed.trees ?? {} };
      }
    } catch {
      // corrupt or unavailable storage — start empty
    }
    return { menus: {}, trees: {} };
  }

  private write(): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.store));
    } catch {
      // quota exceeded / storage unavailable — keep serving from memory
    }
  }
}
