import { Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../../core/auth.service';
import { Client, ClientService } from '../clients/client.service';
import { Location, LocationService, defaultLocation } from '../locations/location.service';
import {
  BridgeDevice,
  ConnectionType,
  Integration,
  IntegrationService,
  IntegrationType,
} from './integration.service';
import { ConfirmDialog } from '../../../../shared/confirm-dialog/confirm-dialog';

@Component({
  selector: 'app-integrations-page',
  imports: [ReactiveFormsModule, ConfirmDialog],
  templateUrl: './integrations-page.html',
  styleUrl: './integrations-page.scss',
})
export class IntegrationsPage {
  private readonly auth = inject(AuthService);
  private readonly clientService = inject(ClientService);
  private readonly locationService = inject(LocationService);
  private readonly integrationService = inject(IntegrationService);

  // --- device type (custom combo, per row) ---
  readonly typeOptions: { value: IntegrationType; label: string }[] = [
    { value: 'CASH_REGISTER', label: 'Cash Register' },
    { value: 'PRINTER', label: 'Printer' },
    { value: 'MOBILE', label: 'Mobile' },
  ];
  typeLabel(t: IntegrationType): string {
    return this.typeOptions.find((o) => o.value === t)?.label ?? '';
  }

  // --- connection type (USB/TCP, custom combo, per row) ---
  readonly connectionOptions: { value: ConnectionType; label: string }[] = [
    { value: 'TCP', label: 'TCP' },
    { value: 'MOBILE', label: 'Mobile' },
  ];
  connectionLabel(c: ConnectionType): string {
    return this.connectionOptions.find((o) => o.value === c)?.label ?? c;
  }

  /** The location's MOBILE rows — what a register / printer can attach to. */
  readonly mobiles = computed(() => this.items().filter((i) => i.type === 'MOBILE'));
  mobileName(bridgeId: string | null): string {
    return this.mobiles().find((m) => m.id === bridgeId)?.name ?? '';
  }

  // --- mobile combos (rows with connection Mobile): pick a MOBILE row of the location ---
  readonly draftBridgeId = signal<string>('');
  readonly editBridgeId = signal<string>('');
  readonly draftMobileComboOpen = signal(false);
  readonly editMobileComboOpen = signal(false);
  toggleDraftMobileCombo(): void {
    this.draftMobileComboOpen.update((o) => !o);
  }
  closeDraftMobileCombo(): void {
    this.draftMobileComboOpen.set(false);
  }
  selectDraftMobile(id: string): void {
    this.draftBridgeId.set(id);
    this.draftMobileComboOpen.set(false);
  }
  toggleEditMobileCombo(): void {
    this.editMobileComboOpen.update((o) => !o);
  }
  closeEditMobileCombo(): void {
    this.editMobileComboOpen.set(false);
  }
  selectEditMobile(id: string): void {
    this.editBridgeId.set(id);
    this.editMobileComboOpen.set(false);
  }

  /** Bridges currently connected to the backend — options for the USB device picker. */
  readonly bridgeDevices = signal<BridgeDevice[]>([]);
  private loadBridgeDevices(): void {
    this.integrationService.devices().subscribe({
      next: (devices) => this.bridgeDevices.set(devices),
      error: () => this.bridgeDevices.set([]),
    });
  }
  deviceLabel(deviceId: string | null): string {
    if (!deviceId) {
      return '';
    }
    const known = this.bridgeDevices().find((d) => d.deviceId === deviceId);
    const shortId = deviceId.length > 12 ? deviceId.slice(0, 8) + '…' : deviceId;
    return known?.deviceName ? `${known.deviceName} (${shortId})` : shortId;
  }

  // --- device combos (USB rows): pick a connected bridge by name, store its id ---
  readonly draftDeviceComboOpen = signal(false);
  readonly editDeviceComboOpen = signal(false);
  toggleDraftDeviceCombo(): void {
    this.loadBridgeDevices();
    this.draftDeviceComboOpen.update((o) => !o);
  }
  closeDraftDeviceCombo(): void {
    this.draftDeviceComboOpen.set(false);
  }
  selectDraftDevice(deviceId: string): void {
    this.draftDeviceId.setValue(deviceId);
    this.draftDeviceComboOpen.set(false);
  }
  toggleEditDeviceCombo(): void {
    this.loadBridgeDevices();
    this.editDeviceComboOpen.update((o) => !o);
  }
  closeEditDeviceCombo(): void {
    this.editDeviceComboOpen.set(false);
  }
  selectEditDevice(deviceId: string): void {
    this.editDeviceId.setValue(deviceId);
    this.editDeviceComboOpen.set(false);
  }

  readonly isSuper = this.auth.isSuper;
  readonly ownClientId = computed(() => (this.isSuper() ? '' : this.auth.clientId() ?? ''));

  readonly error = signal<string | null>(null);
  readonly loading = signal(false);

  // --- client combo (SUPER only) ---
  readonly clients = signal<Client[]>([]);
  readonly clientId = signal<string>('');
  readonly clientComboOpen = signal(false);
  readonly clientSearch = signal('');
  readonly clientOptions = computed(() => {
    const q = this.clientSearch().trim().toLowerCase();
    return (q ? this.clients().filter((c) => c.name.toLowerCase().includes(q)) : this.clients()).slice(0, 5);
  });
  readonly clientName = computed(
    () => this.clients().find((c) => c.id === this.clientId())?.name ?? 'Select a client…',
  );
  readonly clientChosen = computed(() => (this.isSuper() ? !!this.clientId() : true));

  // --- location combo ---
  readonly locations = signal<Location[]>([]);
  readonly locationId = signal<string>('');
  readonly locationComboOpen = signal(false);
  readonly locationSearch = signal('');
  readonly locationOptions = computed(() => {
    const q = this.locationSearch().trim().toLowerCase();
    return (q ? this.locations().filter((l) => l.name.toLowerCase().includes(q)) : this.locations()).slice(0, 5);
  });
  readonly locationName = computed(
    () => this.locations().find((l) => l.id === this.locationId())?.name ?? 'Select a location…',
  );

  // --- table ---
  readonly items = signal<Integration[]>([]);
  readonly draft = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly pendingDelete = signal<Integration | null>(null);

  readonly draftName = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  readonly draftIp = new FormControl('', { nonNullable: true });
  readonly draftDeviceId = new FormControl('', { nonNullable: true });
  readonly draftType = signal<IntegrationType>('CASH_REGISTER');
  readonly draftTypeComboOpen = signal(false);
  readonly draftConnection = signal<ConnectionType>('TCP');
  readonly draftConnectionComboOpen = signal(false);
  readonly editName = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  readonly editIp = new FormControl('', { nonNullable: true });
  readonly editDeviceId = new FormControl('', { nonNullable: true });
  readonly editType = signal<IntegrationType>('CASH_REGISTER');
  readonly editTypeComboOpen = signal(false);
  readonly editConnection = signal<ConnectionType>('TCP');
  readonly editConnectionComboOpen = signal(false);

  // per-row type combos
  toggleDraftTypeCombo(): void {
    this.draftTypeComboOpen.update((o) => !o);
  }
  closeDraftTypeCombo(): void {
    this.draftTypeComboOpen.set(false);
  }
  selectDraftType(t: IntegrationType): void {
    this.draftType.set(t);
    this.draftTypeComboOpen.set(false);
  }
  toggleEditTypeCombo(): void {
    this.editTypeComboOpen.update((o) => !o);
  }
  closeEditTypeCombo(): void {
    this.editTypeComboOpen.set(false);
  }
  selectEditType(t: IntegrationType): void {
    this.editType.set(t);
    this.editTypeComboOpen.set(false);
  }
  toggleDraftConnectionCombo(): void {
    this.draftConnectionComboOpen.update((o) => !o);
  }
  closeDraftConnectionCombo(): void {
    this.draftConnectionComboOpen.set(false);
  }
  selectDraftConnection(c: ConnectionType): void {
    this.draftConnection.set(c);
    this.draftConnectionComboOpen.set(false);
  }
  toggleEditConnectionCombo(): void {
    this.editConnectionComboOpen.update((o) => !o);
  }
  closeEditConnectionCombo(): void {
    this.editConnectionComboOpen.set(false);
  }
  selectEditConnection(c: ConnectionType): void {
    this.editConnection.set(c);
    this.editConnectionComboOpen.set(false);
  }

  constructor() {
    if (this.isSuper()) {
      this.clientService.list().subscribe({
        next: (clients) => {
          this.clients.set(clients);
          if (clients.length === 1) {
            this.selectClient(clients[0].id);
          }
        },
        error: () => this.error.set('Failed to load clients.'),
      });
    } else {
      this.loadLocations(this.ownClientId());
    }
  }

  // --- client combo ---
  toggleClientCombo(): void {
    this.clientSearch.set('');
    this.clientComboOpen.update((o) => !o);
  }
  closeClientCombo(): void {
    this.clientComboOpen.set(false);
  }
  selectClient(id: string): void {
    this.clientId.set(id);
    this.clientComboOpen.set(false);
    this.resetLocation();
    this.loadLocations(id);
  }
  private loadLocations(clientId: string): void {
    if (!clientId) {
      this.locations.set([]);
      return;
    }
    this.locationService.list(clientId).subscribe({
      next: (locations) => {
        this.locations.set(locations);
        const preferred = defaultLocation(locations);
        if (preferred) {
          this.selectLocation(preferred.id);
        }
      },
      error: () => this.error.set('Failed to load locations.'),
    });
  }
  private resetLocation(): void {
    this.locationId.set('');
    this.locations.set([]);
    this.resetTable();
  }

  // --- location combo ---
  toggleLocationCombo(): void {
    this.locationSearch.set('');
    this.locationComboOpen.update((o) => !o);
  }
  closeLocationCombo(): void {
    this.locationComboOpen.set(false);
  }
  selectLocation(id: string): void {
    this.locationId.set(id);
    this.locationComboOpen.set(false);
    this.resetTable();
    this.loadItems(id);
  }
  private resetTable(): void {
    this.items.set([]);
    this.draft.set(false);
    this.editingId.set(null);
    this.error.set(null);
  }
  private loadItems(locationId: string): void {
    this.loading.set(true);
    this.integrationService.list(locationId).subscribe({
      next: (rows) => {
        this.items.set(rows);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load integrations.');
        this.loading.set(false);
      },
    });
  }

  // --- create ---
  startCreate(): void {
    this.editingId.set(null);
    this.draftName.reset();
    this.draftIp.reset();
    this.draftDeviceId.reset();
    this.draftType.set('CASH_REGISTER');
    this.draftTypeComboOpen.set(false);
    this.draftConnection.set('TCP');
    this.draftConnectionComboOpen.set(false);
    this.draftBridgeId.set('');
    this.draftMobileComboOpen.set(false);
    this.error.set(null);
    this.draft.set(true);
    this.loadBridgeDevices();
  }
  cancelCreate(): void {
    this.draftTypeComboOpen.set(false);
    this.draftConnectionComboOpen.set(false);
    this.draft.set(false);
  }
  saveCreate(): void {
    if (this.draftName.invalid || !this.locationId()) {
      return;
    }
    this.integrationService
      .create({
        locationId: this.locationId(),
        name: this.draftName.value.trim(),
        ip: this.draftIp.value.trim() || null,
        type: this.draftType(),
        connection: this.draftType() === 'MOBILE' ? 'TCP' : this.draftConnection(),
        deviceId: this.draftType() === 'MOBILE' ? this.draftDeviceId.value.trim() || null : null,
        bridgeId:
          this.draftType() !== 'MOBILE' && this.draftConnection() === 'MOBILE' ? this.draftBridgeId() || null : null,
      })
      .subscribe({
        next: (item) => {
          this.items.update((list) => this.sorted([...list, item]));
          this.draft.set(false);
        },
        error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'create')),
      });
  }

  // --- edit ---
  startEdit(item: Integration): void {
    this.draft.set(false);
    this.editingId.set(item.id);
    this.editName.setValue(item.name);
    this.editIp.setValue(item.ip ?? '');
    this.editDeviceId.setValue(item.deviceId ?? '');
    this.editType.set(item.type);
    this.editTypeComboOpen.set(false);
    this.editConnection.set(item.connection ?? 'TCP');
    this.editConnectionComboOpen.set(false);
    this.editBridgeId.set(item.bridgeId ?? '');
    this.editMobileComboOpen.set(false);
    this.error.set(null);
    this.loadBridgeDevices();
  }
  cancelEdit(): void {
    this.editTypeComboOpen.set(false);
    this.editConnectionComboOpen.set(false);
    this.editingId.set(null);
  }
  saveEdit(item: Integration): void {
    if (this.editName.invalid) {
      return;
    }
    this.integrationService
      .update(item.id, {
        locationId: item.locationId,
        name: this.editName.value.trim(),
        ip: this.editIp.value.trim() || null,
        type: this.editType(),
        connection: this.editType() === 'MOBILE' ? 'TCP' : this.editConnection(),
        deviceId: this.editType() === 'MOBILE' ? this.editDeviceId.value.trim() || null : null,
        bridgeId:
          this.editType() !== 'MOBILE' && this.editConnection() === 'MOBILE' ? this.editBridgeId() || null : null,
      })
      .subscribe({
        next: (updated) => {
          this.items.update((list) => this.sorted(list.map((i) => (i.id === updated.id ? updated : i))));
          this.editingId.set(null);
        },
        error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'update')),
      });
  }

  // --- delete ---
  remove(item: Integration): void {
    this.error.set(null);
    this.pendingDelete.set(item);
  }
  cancelDelete(): void {
    this.pendingDelete.set(null);
  }
  confirmDelete(): void {
    const item = this.pendingDelete();
    if (!item) {
      return;
    }
    this.pendingDelete.set(null);
    this.integrationService.delete(item.id).subscribe({
      next: () => this.items.update((list) => list.filter((i) => i.id !== item.id)),
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'delete')),
    });
  }

  private sorted(list: Integration[]): Integration[] {
    return [...list].sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true }));
  }

  private message(err: HttpErrorResponse, action: string): string {
    if (err.status === 400) {
      return 'Please check the fields.';
    }
    return `Failed to ${action} integration.`;
  }
}
