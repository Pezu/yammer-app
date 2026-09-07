import { Component, computed, effect, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { PaymentReportRow, PaymentReportService } from './payment-report.service';
import { Client, ClientService } from '../clients/client.service';
import { Location, LocationService } from '../locations/location.service';
import { AuthService } from '../../../../core/auth.service';
import { ComboBox } from '../../../../shared/combo-box';

/** Payments taken at a location, newest first: table, waiter, amount, tip, total, type. */
@Component({
  selector: 'app-payments-report-page',
  imports: [DecimalPipe, ComboBox],
  templateUrl: './payments-report-page.html',
  styleUrl: './payments-report-page.scss',
})
export class PaymentsReportPage {
  private readonly reportService = inject(PaymentReportService);
  private readonly clientService = inject(ClientService);
  private readonly locationService = inject(LocationService);
  private readonly auth = inject(AuthService);

  readonly isSuper = this.auth.isSuper;
  readonly ownClientId = computed(() => (this.isSuper() ? '' : this.auth.clientId() ?? ''));

  readonly rows = signal<PaymentReportRow[]>([]);
  readonly clients = signal<Client[]>([]);
  readonly locations = signal<Location[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly clientFilter = signal<string>('');
  readonly locationFilter = signal<string>('');
  readonly headerLocationOptions = computed(() =>
    this.locationsFor(this.isSuper() ? this.clientFilter() : this.ownClientId()),
  );
  readonly showClientCombo = this.isSuper;
  readonly showLocationCombo = computed(() => !this.isSuper() || !!this.clientFilter());

  readonly totals = computed(() => ({
    amount: this.rows().reduce((s, r) => s + r.amount, 0),
    tip: this.rows().reduce((s, r) => s + r.tip, 0),
    total: this.rows().reduce((s, r) => s + r.total, 0),
  }));

  constructor() {
    this.clientService.list().subscribe({
      next: (clients) => {
        this.clients.set(clients);
        if (this.isSuper() && clients.length === 1 && !this.clientFilter()) {
          this.clientFilter.set(clients[0].id);
        }
      },
    });
    this.locationService.list().subscribe({
      next: (locations) => this.locations.set(locations),
    });
    effect(() => {
      const options = this.headerLocationOptions();
      if (options.length === 1 && !this.locationFilter()) {
        this.locationFilter.set(options[0].id);
      }
    });
    effect(() => {
      const locationId = this.locationFilter();
      if (locationId) {
        this.load(locationId);
      } else {
        this.rows.set([]);
      }
    });
  }

  selectClient(id: string): void {
    this.clientFilter.set(id);
    this.locationFilter.set('');
  }

  load(locationId: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.reportService.list(locationId).subscribe({
      next: (rows) => {
        this.rows.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load payments.');
        this.loading.set(false);
      },
    });
  }

  private locationsFor(clientId: string): Location[] {
    return clientId ? this.locations().filter((l) => l.clientId === clientId) : [];
  }
}
