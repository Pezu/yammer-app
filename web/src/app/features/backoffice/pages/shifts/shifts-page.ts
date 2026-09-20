import { Component, OnDestroy, computed, effect, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Shift, ShiftService } from './shift.service';
import { Client, ClientService } from '../clients/client.service';
import { Location, LocationService, defaultLocation } from '../locations/location.service';
import { Integration, IntegrationService } from '../integrations/integration.service';
import { AuthService } from '../../../../core/auth.service';
import { ComboBox } from '../../../../shared/combo-box';
import { ConfirmDialog } from '../../../../shared/confirm-dialog/confirm-dialog';
import { roDateTime } from '../../../../shared/tz';

/**
 * Configuration → Shifts: a location's service days. A manager starts one ("+", with an
 * optional nickname) and closes it once every table is closed; closing cashes up — one
 * thermal slip per waiter, on the printer picked inline on the open row — and stamps the
 * day shut.
 *
 * The open day is also the window the waiter's own Payments/Statistics pages report on,
 * which is why those screens carry no date picker.
 */
@Component({
  selector: 'app-shifts-page',
  imports: [ReactiveFormsModule, ComboBox, ConfirmDialog],
  templateUrl: './shifts-page.html',
  styleUrl: './shifts-page.scss',
})
export class ShiftsPage implements OnDestroy {
  private readonly shiftService = inject(ShiftService);
  private readonly clientService = inject(ClientService);
  private readonly locationService = inject(LocationService);
  private readonly integrationService = inject(IntegrationService);
  private readonly auth = inject(AuthService);

  readonly isSuper = this.auth.isSuper;
  private readonly ownClientId = computed(() => (this.isSuper() ? '' : this.auth.clientId() ?? ''));

  readonly shifts = signal<Shift[]>([]);
  readonly blockers = signal<string[]>([]);
  readonly printers = signal<Integration[]>([]);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly busy = signal(false);

  readonly clientId = signal<string>('');
  readonly locationId = signal<string>('');
  /** Which thermal printer the cash-up slips go to; '' = do not print. */
  readonly printerId = signal<string>('');

  private readonly clients = signal<Client[]>([]);
  private readonly locations = signal<Location[]>([]);
  readonly clientOptions = computed(() => this.clients().map((c) => ({ id: c.id, name: c.name })));
  readonly locationOptions = computed(() =>
    this.locationsFor(this.isSuper() ? this.clientId() : this.ownClientId()).map((l) => ({
      id: l.id,
      name: l.name,
    })),
  );
  readonly printerOptions = computed(() => this.printers().map((p) => ({ id: p.id, name: p.name })));
  readonly showClientCombo = this.isSuper;
  readonly showLocationCombo = computed(() => !this.isSuper() || !!this.clientId());

  /** The location's running day, if any — at most one can be open. */
  readonly openShift = computed(() => this.shifts().find((s) => s.open) ?? null);
  readonly dayOpen = computed(() => !!this.openShift());

  // --- add / rename ---
  readonly draft = signal(false);
  readonly draftNickname = new FormControl('', { nonNullable: true });
  readonly editingId = signal<string | null>(null);
  readonly editNickname = new FormControl('', { nonNullable: true });
  readonly confirmClose = signal(false);

  readonly closeMessage = computed(() => {
    const printer = this.printers().find((p) => p.id === this.printerId());
    return printer
      ? `Every table is closed. The day's takings will be cashed up and one slip per waiter printed on ${printer.name}.`
      : "Every table is closed. The day's takings will be cashed up. No printer is picked on the row, so no slips will print.";
  });

  constructor() {
    this.clientService.list().subscribe({
      next: (clients) => {
        this.clients.set(clients);
        if (this.isSuper() && clients.length === 1 && !this.clientId()) {
          this.clientId.set(clients[0].id);
        }
      },
    });
    this.locationService.list().subscribe({ next: (l) => this.locations.set(l) });
    effect(() => {
      const preferred = defaultLocation(
        this.locationsFor(this.isSuper() ? this.clientId() : this.ownClientId()),
      );
      if (preferred && !this.locationId()) {
        this.locationId.set(preferred.id);
      }
    });
    effect(() => {
      const locationId = this.locationId();
      if (locationId) {
        this.load(locationId);
        this.loadPrinters(locationId);
      } else {
        this.shifts.set([]);
        this.blockers.set([]);
        this.printers.set([]);
      }
    });
    // waiters keep closing tables while this page is open: keep the blockers (and with them
    // the Close button) current, or the manager stares at a button that is stale-disabled
    this.blockerPoll = setInterval(() => {
      const locationId = this.locationId();
      if (locationId && this.dayOpen() && !this.busy()) {
        this.shiftService.openTables(locationId).subscribe({
          next: (tables) => this.blockers.set(tables),
          error: () => {
            /* keep the last list */
          },
        });
      }
    }, 15_000);
  }

  private readonly blockerPoll: ReturnType<typeof setInterval>;

  ngOnDestroy(): void {
    clearInterval(this.blockerPoll);
  }

  selectClient(id: string): void {
    this.clientId.set(id);
    this.locationId.set('');
  }

  moment(iso: string): string {
    return roDateTime(iso);
  }

  private locationsFor(clientId: string): Location[] {
    return clientId ? this.locations().filter((l) => l.clientId === clientId) : [];
  }

  private loadPrinters(locationId: string): void {
    this.integrationService.list(locationId, 'PRINTER').subscribe({
      next: (list) => {
        this.printers.set(list);
        if (!this.printerId() && list.length) this.printerId.set(list[0].id);
      },
      error: () => this.printers.set([]),
    });
  }

  private load(locationId: string): void {
    this.error.set(null);
    this.shiftService.list(locationId).subscribe({
      next: (shifts) => this.shifts.set(shifts),
      error: () => this.error.set('Failed to load the service days.'),
    });
    this.shiftService.openTables(locationId).subscribe({
      next: (tables) => this.blockers.set(tables),
      error: () => this.blockers.set([]),
    });
  }

  // --- start a day ---

  startCreate(): void {
    this.editingId.set(null);
    this.draftNickname.setValue('');
    this.error.set(null);
    this.notice.set(null);
    this.draft.set(true);
  }

  cancelCreate(): void {
    this.draft.set(false);
  }

  saveCreate(): void {
    const locationId = this.locationId();
    if (!locationId || this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    this.shiftService.open(locationId, this.draftNickname.value).subscribe({
      next: () => {
        this.busy.set(false);
        this.draft.set(false);
        this.notice.set('Service day started.');
        this.load(locationId);
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(err?.error?.message || 'Could not start the service day.');
      },
    });
  }

  // --- rename ---

  startEdit(s: Shift): void {
    this.draft.set(false);
    this.error.set(null);
    this.editNickname.setValue(s.nickname ?? '');
    this.editingId.set(s.id);
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  saveEdit(s: Shift): void {
    this.shiftService.rename(s.id, this.editNickname.value).subscribe({
      next: (updated) => {
        this.shifts.update((list) => list.map((x) => (x.id === updated.id ? updated : x)));
        this.editingId.set(null);
      },
      error: (err) => this.error.set(err?.error?.message || 'Could not rename the service day.'),
    });
  }

  // --- close & cash up ---

  askClose(): void {
    this.error.set(null);
    this.notice.set(null);
    this.confirmClose.set(true);
  }

  closeDay(): void {
    const locationId = this.locationId();
    this.confirmClose.set(false);
    if (!locationId || this.busy()) return;
    this.busy.set(true);
    this.shiftService.close(locationId, this.printerId() || undefined).subscribe({
      next: (result) => {
        this.busy.set(false);
        const cashedUp = `Service day closed — ${result.rows.length} waiter(s) cashed up`;
        this.notice.set(
          result.printed
            ? `${cashedUp}, slips sent to the printer.`
            : result.printError
              ? `${cashedUp}. Slips NOT printed: ${result.printError}`
              : `${cashedUp}.`,
        );
        this.load(locationId);
      },
      error: (err) => {
        this.busy.set(false);
        // 409 while tables are still open: the message names them
        this.error.set(err?.error?.message || 'Could not close the service day.');
        this.load(locationId);
      },
    });
  }
}
