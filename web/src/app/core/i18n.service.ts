import { Injectable, signal } from '@angular/core';
import { EN, RO, TKey } from './i18n/translations';

export type Lang = 'ro' | 'en';

/** Languages offered in the selector — add an entry (+ a dictionary) to offer another one. */
export interface LangOption {
  id: Lang;
  flag: string;
  label: string;
}
export const LANGS: LangOption[] = [
  { id: 'ro', flag: '🇷🇴', label: 'Română' },
  { id: 'en', flag: '🇬🇧', label: 'English' },
];

/** Combo-box options for the language selector: flag + native name. */
export const LANG_OPTIONS = LANGS.map((l) => ({ id: l.id, name: `${l.flag} ${l.label}` }));

export function isLang(value: string): value is Lang {
  return LANGS.some((l) => l.id === value);
}

/** Who the language choice belongs to — each is remembered separately in localStorage. */
export type I18nScope = 'waiter' | 'customer';

/**
 * Runtime translations for the waiter and customer surfaces (RO / EN). `lang` is a
 * signal, so `t()` calls in templates re-render when the language changes — no
 * per-locale build, no reload. Waiters default to RO; customers follow the browser
 * language (RO fallback). A change is remembered per scope.
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  readonly lang = signal<Lang>('ro');
  private scope: I18nScope = 'waiter';

  /** Pick the scope's remembered language (or its default). Call once per surface. */
  init(scope: I18nScope): void {
    this.scope = scope;
    this.lang.set(this.stored(scope) ?? this.defaultFor(scope));
  }

  setLang(lang: Lang): void {
    this.lang.set(lang);
    try {
      localStorage.setItem(this.key(this.scope), lang);
    } catch {
      /* private mode etc. — the choice just won't survive the tab */
    }
  }

  /** Translate a key in the current language, filling `{name}` placeholders. */
  readonly t = (key: TKey, params?: Record<string, string | number>): string => {
    const dict = this.lang() === 'ro' ? RO : EN;
    let text: string = dict[key] ?? EN[key] ?? key;
    if (params) {
      for (const [name, value] of Object.entries(params)) {
        text = text.split(`{${name}}`).join(String(value));
      }
    }
    return text;
  };

  private defaultFor(scope: I18nScope): Lang {
    if (scope === 'customer' && typeof navigator !== 'undefined') {
      return (navigator.language ?? '').toLowerCase().startsWith('en') ? 'en' : 'ro';
    }
    return 'ro';
  }

  private stored(scope: I18nScope): Lang | null {
    try {
      const v = localStorage.getItem(this.key(scope));
      return v && isLang(v) ? v : null;
    } catch {
      return null;
    }
  }

  private key(scope: I18nScope): string {
    return `yammer.lang.${scope}`;
  }
}
