import { parseUtc, roDate } from './tz';
import { EN, type TKey } from '../core/i18n/translations';

type Translate = (key: TKey, params?: Record<string, string | number>) => string;

/** English fallback for surfaces without a language switch (e.g. the service board). */
const englishT: Translate = (key, params) => {
  let text: string = EN[key];
  for (const [name, value] of Object.entries(params ?? {})) {
    text = text.split(`{${name}}`).join(String(value));
  }
  return text;
};

/**
 * Human relative time: "just now" (<1m), "x min ago" (<60m), "x hours ago" (<24h),
 * otherwise the date as "dd.MM.YYYY" (Romanian local time). The backend timestamp is
 * naive UTC, so it is parsed as UTC before any elapsed-time math. Wording comes from
 * the caller's translator so it follows the chosen language.
 */
export function timeAgo(iso: string, t: Translate = englishT): string {
  const d = parseUtc(iso);
  if (!d) return '';
  const min = Math.floor((Date.now() - d.getTime()) / 60000);
  if (min < 1) return t('time.justNow');
  if (min < 60) return t('time.minAgo', { n: min });
  const hours = Math.floor(min / 60);
  if (hours < 24) return hours === 1 ? t('time.hourAgo') : t('time.hoursAgo', { n: hours });
  return roDate(d);
}
