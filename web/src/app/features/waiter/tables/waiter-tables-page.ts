import { Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { I18nService } from '../../../core/i18n.service';
import { AuthService } from '../../../core/auth.service';
import {
  AssignableOrderPoint,
  OrderPoint,
  OrderPointService,
} from '../../backoffice/pages/order-points/order-point.service';
import {
  OrderPointType,
  OrderPointTypeService,
} from '../../backoffice/pages/order-point-types/order-point-type.service';

/**
 * The waiter's ASSIGNED tables and bars as square tiles, plus a picker (the "+"
 * tile) to assign more from the location's full table list. Single-user points
 * accept one waiter and show who holds them; multi-user points always accept
 * and show how many waiters are on them.
 */
@Component({
  selector: 'app-waiter-tables-page',
  templateUrl: './waiter-tables-page.html',
  styleUrl: './waiter-tables-page.scss',
})
export class WaiterTablesPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly orderPointService = inject(OrderPointService);
  private readonly typeService = inject(OrderPointTypeService);
  readonly t = inject(I18nService).t;

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly assigned = signal<OrderPoint[]>([]);
  readonly types = signal<OrderPointType[]>([]);

  /** The waiter's home location, from the session (set at login). */
  readonly locationId = this.auth.session()?.locationId ?? null;

  private readonly typeById = computed(() => new Map(this.types().map((t) => [t.id, t.type])));

  constructor() {
    this.typeService.list().subscribe({
      next: (types) => this.types.set(types),
    });
    this.loadAssigned();
  }

  /** Enter one of my tables — its orders + the place-order flow. */
  open(point: OrderPoint): void {
    this.router.navigate(['/waiter/tables', point.id], { state: { name: point.name } });
  }

  loadAssigned(): void {
    this.loading.set(true);
    this.error.set(null);
    this.orderPointService.assigned().subscribe({
      next: (points) => {
        this.assigned.set(points);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(this.t('tables.loadFailed'));
        this.loading.set(false);
      },
    });
  }

  // --- split (tables only) ---

  /** id of the table whose split call is in flight. */
  readonly splitting = signal<string | null>(null);

  /** Only TABLE points named T{n}.{m} can be split into a new slot. */
  canSplit(point: OrderPoint): boolean {
    return this.typeById().get(point.typeId) === 'TABLE' && /^[A-Za-z]+\d+\.\d+$/.test(point.name);
  }

  /** Create the next slot of this table (T12.1 → T12.2); it is assigned to me and shows up as a tile. */
  split(point: OrderPoint, event: Event): void {
    event.stopPropagation();
    if (this.splitting()) {
      return;
    }
    this.error.set(null);
    this.splitting.set(point.id);
    this.orderPointService.split(point.id).subscribe({
      next: () => {
        this.splitting.set(null);
        this.loadAssigned();
      },
      error: () => {
        this.splitting.set(null);
        this.error.set(this.t('tables.splitFailed'));
      },
    });
  }

  // --- assign picker ---

  readonly pickerOpen = signal(false);
  readonly pickerLoading = signal(false);
  readonly board = signal<AssignableOrderPoint[]>([]);
  /** id of the point whose assign/unassign call is in flight. */
  readonly busy = signal<string | null>(null);

  /** TABLE and BAR points of the location, with assignment state. */
  readonly pickerTiles = computed(() =>
    this.board().filter((p) => {
      const type = this.typeById().get(p.typeId);
      return type === 'TABLE' || type === 'BAR';
    }),
  );

  openPicker(): void {
    if (!this.locationId) {
      return;
    }
    this.board.set([]); // fresh board each time the picker opens
    this.pickerOpen.set(true);
    this.loadBoard();
  }

  closePicker(): void {
    this.pickerOpen.set(false);
    this.loadAssigned();
  }

  /**
   * Load the picker board. The "Loading…" placeholder is shown only on the first load;
   * refreshes after an assign/unassign update the tiles in place so the picker never
   * flashes away and back.
   */
  private loadBoard(): void {
    if (!this.locationId) {
      return;
    }
    if (this.board().length === 0) {
      this.pickerLoading.set(true);
    }
    this.orderPointService.assignable(this.locationId).subscribe({
      next: (board) => {
        this.board.set(board);
        this.pickerLoading.set(false);
      },
      error: () => {
        this.error.set(this.t('tables.listFailed'));
        this.pickerLoading.set(false);
      },
    });
  }

  /** A tile can be tapped unless it is a single-user point already taken by someone else. */
  canToggle(point: AssignableOrderPoint): boolean {
    return point.allowMultipleUsers || point.assignedToMe || point.assignedCount === 0;
  }

  statusOf(point: AssignableOrderPoint): string {
    if (point.allowMultipleUsers) {
      return point.assignedCount === 1
        ? this.t('tables.assignedOne')
        : this.t('tables.assignedMany', { n: point.assignedCount });
    }
    if (point.assignedToMe) {
      return this.t('tables.yours');
    }
    return point.assignedCount > 0
      ? this.t('tables.taken', { name: point.assignedNames[0] })
      : this.t('tables.free');
  }

  toggle(point: AssignableOrderPoint): void {
    if (!this.canToggle(point) || this.busy()) {
      return;
    }
    this.busy.set(point.id);
    const call = point.assignedToMe
      ? this.orderPointService.unassign(point.id)
      : this.orderPointService.assign(point.id);
    call.subscribe({
      next: () => {
        this.busy.set(null);
        this.loadBoard();
      },
      error: (err) => {
        this.busy.set(null);
        // a 409 on unassign = the table's session is open; only Close table frees it
        this.error.set(
          point.assignedToMe && err?.status === 409
            ? this.t('tables.openRefused')
            : this.t('tables.assignFailed'),
        );
        this.loadBoard();
      },
    });
  }
}
