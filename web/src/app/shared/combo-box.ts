import { Component, computed, input, output, signal } from '@angular/core';

export interface ComboOption {
  id: string;
  name: string;
}

/**
 * Custom dropdown styled like duralux's Select2 (same look as the header client
 * combo in `_crud-table.scss`) — use this everywhere instead of a native <select>.
 */
@Component({
  selector: 'app-combo-box',
  template: `
    <div class="combo">
      <button
        type="button"
        class="combo-trigger"
        [class.placeholder]="isEmpty()"
        (click)="toggle()"
      >
        <span class="label" [innerHTML]="selectedLabel()"></span>
        <svg class="caret" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"></polyline></svg>
      </button>

      @if (open()) {
        <div class="combo-backdrop" (click)="close()"></div>
        <div class="combo-menu">
          @if (searchable()) {
            <div class="combo-search">
              <input
                type="text"
                #q
                [placeholder]="searchPlaceholder()"
                [value]="search()"
                (input)="search.set(q.value)"
                (keyup.escape)="close()"
                autofocus
              />
            </div>
          }
          <ul class="combo-list">
            @if (allowEmpty() && !multiple()) {
              <li>
                <button type="button" class="combo-option" [class.active]="!value()" (click)="select('')">
                  {{ emptyLabel() }}
                </button>
              </li>
            }
            @for (option of filtered(); track option.id) {
              <li>
                <button
                  type="button"
                  class="combo-option"
                  [class.active]="isSelected(option.id)"
                  (click)="select(option.id)"
                >
                  <span [innerHTML]="option.name"></span>
                  @if (multiple() && isSelected(option.id)) {
                    <svg class="tick" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>
                  }
                </button>
              </li>
            } @empty {
              <li class="combo-empty">{{ noMatchLabel() }}</li>
            }
          </ul>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .combo {
        position: relative;
        width: 100%;
      }

      .combo-trigger {
        display: inline-flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.75rem;
        width: 100%;
        padding: 0.45rem 0.75rem;
        font: inherit;
        font-size: 0.85rem;
        font-weight: 500;
        color: var(--text);
        background: #fff;
        border: 1px solid var(--border);
        border-radius: 4px;
        cursor: pointer;
      }

      .combo-trigger.placeholder {
        color: var(--muted);
        font-weight: 400;
      }

      .combo-trigger .label {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .combo-trigger .caret {
        color: var(--muted);
        flex: none;
      }

      .combo-backdrop {
        position: fixed;
        inset: 0;
        z-index: 20;
      }

      .combo-menu {
        position: absolute;
        top: calc(100% + 4px);
        left: 0;
        z-index: 21;
        min-width: 100%;
        background: #fff;
        border: 1px solid var(--border);
        border-radius: 6px;
        box-shadow: 0 0.5rem 1.5rem rgba(18, 27, 46, 0.15);
        overflow: hidden;
      }

      .combo-search {
        padding: 0.5rem;
        border-bottom: 1px solid var(--border);
      }

      .combo-search input {
        width: 100%;
        padding: 0.4rem 0.6rem;
        font-size: 0.85rem;
        color: var(--text);
        background: #fff;
        border: 1px solid var(--border);
        border-radius: 4px;
      }

      .combo-search input:focus {
        outline: none;
        border-color: var(--primary);
      }

      .combo-list {
        margin: 0;
        padding: 0.25rem;
        max-height: 200px;
        overflow-y: auto;
        list-style: none;
      }

      .combo-empty {
        padding: 0.6rem 0.65rem;
        font-size: 0.85rem;
        color: var(--muted);
      }

      .combo-option {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.5rem;
        width: 100%;
        padding: 0.5rem 0.65rem;
        font: inherit;
        font-size: 0.85rem;
        text-align: left;
        color: var(--text);
        background: none;
        border: none;
        border-radius: 4px;
        cursor: pointer;
        white-space: nowrap;
      }

      .combo-option .tick {
        flex: none;
      }

      .combo-option:hover {
        background: var(--page-bg);
      }

      .combo-option.active {
        color: var(--primary);
        background: rgba(52, 84, 209, 0.08);
        font-weight: 600;
      }
    `,
  ],
})
export class ComboBox {
  readonly options = input<ComboOption[]>([]);
  readonly value = input<string>('');
  /** Multi-select mode: bind [values]/(valuesChange) instead of [value]/(valueChange). */
  readonly multiple = input(false);
  readonly values = input<string[]>([]);
  readonly placeholder = input('Select…');
  /** Show a clearable "none" option at the top of the list. */
  readonly allowEmpty = input(false);
  readonly emptyLabel = input('—');
  readonly searchPlaceholder = input('Search…');
  readonly noMatchLabel = input('No matches');

  readonly valueChange = output<string>();
  readonly valuesChange = output<string[]>();

  readonly open = signal(false);
  readonly search = signal('');

  readonly isEmpty = computed(() =>
    this.multiple() ? this.values().length === 0 : !this.value(),
  );

  readonly selectedLabel = computed(() => {
    if (this.multiple()) {
      const names = this.options()
        .filter((o) => this.values().includes(o.id))
        .map((o) => o.name);
      return names.length ? names.join(', ') : this.placeholder();
    }
    return this.options().find((o) => o.id === this.value())?.name ?? this.placeholder();
  });

  readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    // Names may carry rich HTML (e.g. product names) — match on the visible text only.
    return q
      ? this.options().filter((o) => o.name.replace(/<[^>]+>/g, '').toLowerCase().includes(q))
      : this.options();
  });

  /** The search box only earns its place once the list is long enough. */
  readonly searchable = computed(() => this.options().length > 5);

  toggle(): void {
    this.search.set('');
    this.open.update((open) => !open);
  }

  close(): void {
    this.open.set(false);
  }

  isSelected(id: string): boolean {
    return this.multiple() ? this.values().includes(id) : id === this.value();
  }

  select(id: string): void {
    if (this.multiple()) {
      // Toggle and stay open so several options can be picked in one go.
      const next = this.values().includes(id)
        ? this.values().filter((v) => v !== id)
        : [...this.values(), id];
      this.valuesChange.emit(next);
      return;
    }
    this.open.set(false);
    this.valueChange.emit(id);
  }
}
