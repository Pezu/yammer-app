import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import {
  CreateOrderPointsBatch,
  OrderPoint,
  OrderPointInput,
  OrderPointService,
  SelfOrderMode,
} from './order-point.service';
import { OrderPointType, OrderPointTypeService } from '../order-point-types/order-point-type.service';
import { Client, ClientService } from '../clients/client.service';
import { Location, LocationService, defaultLocation } from '../locations/location.service';
import { Menu, MenuService } from '../menu/menu.service';
import { Integration, IntegrationService } from '../integrations/integration.service';
import { PaymentType, PaymentTypeService } from '../payment-types/payment-type.service';
import { SelfPayType, SelfPayTypeService } from '../self-pay-types/self-pay-type.service';
import { AuthService } from '../../../../core/auth.service';
import { ConfirmDialog } from '../../../../shared/confirm-dialog/confirm-dialog';
import { ComboBox } from '../../../../shared/combo-box';

@Component({
  selector: 'app-order-points-page',
  imports: [ReactiveFormsModule, ConfirmDialog, ComboBox],
  templateUrl: './order-points-page.html',
  styleUrl: './order-points-page.scss',
})
export class OrderPointsPage {
  private readonly orderPointService = inject(OrderPointService);
  private readonly typeService = inject(OrderPointTypeService);
  private readonly clientService = inject(ClientService);
  private readonly locationService = inject(LocationService);
  private readonly menuService = inject(MenuService);
  private readonly integrationService = inject(IntegrationService);
  private readonly paymentTypeService = inject(PaymentTypeService);
  private readonly selfPayTypeService = inject(SelfPayTypeService);
  private readonly auth = inject(AuthService);

  readonly isSuper = this.auth.isSuper;
  readonly ownClientId = computed(() => (this.isSuper() ? '' : this.auth.clientId() ?? ''));

  readonly orderPoints = signal<OrderPoint[]>([]);
  readonly types = signal<OrderPointType[]>([]);
  readonly clients = signal<Client[]>([]);
  readonly locations = signal<Location[]>([]);
  readonly menus = signal<Menu[]>([]);
  readonly integrations = signal<Integration[]>([]);
  readonly paymentTypes = signal<PaymentType[]>([]);
  readonly selfPayTypes = signal<SelfPayType[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  /** Context: SUPER picks a client; everyone picks a location within it. */
  readonly clientFilter = signal<string>('');
  readonly locationFilter = signal<string>('');
  readonly headerLocationOptions = computed(() =>
    this.locationsFor(this.isSuper() ? this.clientFilter() : this.ownClientId()),
  );
  readonly showClientCombo = this.isSuper;
  readonly showLocationCombo = computed(() => !this.isSuper() || !!this.clientFilter());

  private readonly typeById = computed(() => new Map(this.types().map((t) => [t.id, t.type])));

  /** Types shaped for the combo box ({id, name}). */
  readonly typeOptions = computed(() => this.types().map((t) => ({ id: t.id, name: t.type })));

  /** Payment types shaped for the combo box ({id, name}). */
  readonly paymentTypeOptions = computed(() =>
    this.paymentTypes().map((t) => ({ id: t.id, name: t.type })),
  );

  private readonly paymentTypeById = computed(
    () => new Map(this.paymentTypes().map((t) => [t.id, t.type])),
  );

  paymentTypeName(id: string): string {
    return this.paymentTypeById().get(id) ?? '—';
  }

  /** Self pay types (ONLINE/CHECK) shaped for the combo box ({id, name}). */
  readonly selfPayTypeOptions = computed(() =>
    this.selfPayTypes().map((t) => ({ id: t.id, name: t.type })),
  );

  private readonly selfPayTypeById = computed(
    () => new Map(this.selfPayTypes().map((t) => [t.id, t.type])),
  );

  selfPayTypeName(id: string | null): string {
    return (id && this.selfPayTypeById().get(id)) || '—';
  }


  typeName(id: string): string {
    return this.typeById().get(id) ?? '—';
  }

  private readonly pointById = computed(() => new Map(this.orderPoints().map((p) => [p.id, p.name])));

  pointName(id: string | null): string {
    return (id && this.pointById().get(id)) || '—';
  }

  private readonly menuById = computed(() => new Map(this.menus().map((m) => [m.id, m.name])));
  private readonly integrationById = computed(
    () => new Map(this.integrations().map((i) => [i.id, i.name])),
  );

  menuName(id: string | null): string {
    return (id && this.menuById().get(id)) || '—';
  }

  deviceName(id: string | null): string {
    return (id && this.integrationById().get(id)) || '—';
  }

  /** SERVICE-type order points of the selected location — the "service point" options. */
  readonly serviceOptions = computed(() =>
    this.orderPoints().filter((p) => this.typeName(p.typeId) === 'SERVICE'),
  );

  constructor() {
    this.typeService.list().subscribe({
      next: (types) => this.types.set(types),
    });
    this.paymentTypeService.list().subscribe({
      next: (paymentTypes) => this.paymentTypes.set(paymentTypes),
    });
    this.selfPayTypeService.list().subscribe({
      next: (selfPayTypes) => this.selfPayTypes.set(selfPayTypes),
    });
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
    // Auto-select the location when the context client has exactly one.
    effect(() => {
      const preferred = defaultLocation(this.headerLocationOptions());
      if (preferred && !this.locationFilter()) {
        this.locationFilter.set(preferred.id);
      }
    });
    // (Re)load the order points + the location's menus and peripherals whenever
    // the selected location changes.
    effect(() => {
      const locationId = this.locationFilter();
      if (locationId) {
        this.load(locationId);
        this.menuService.listMenus(locationId).subscribe({
          next: (menus) => this.menus.set(menus),
        });
        this.integrationService.list(locationId).subscribe({
          next: (integrations) => this.integrations.set(integrations),
        });
      } else {
        this.orderPoints.set([]);
        this.menus.set([]);
        this.integrations.set([]);
      }
    });
  }

  selectClient(id: string): void {
    this.clientFilter.set(id);
    this.locationFilter.set(''); // locations belong to the client — reset on switch
  }

  load(locationId: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.orderPointService.list(locationId).subscribe({
      next: (points) => {
        this.orderPoints.set(points);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load order points.');
        this.loading.set(false);
      },
    });
  }

  private locationsFor(clientId: string): Location[] {
    return clientId ? this.locations().filter((l) => l.clientId === clientId) : [];
  }

  /** B1…Bn, S1…Sn, T1.1…Tn.1 — prefix groups alphabetically, numbers numerically. */
  private sorted(list: OrderPoint[]): OrderPoint[] {
    const parse = (name: string) => /^([A-Za-z]+)(\d+)(?:\.(\d+))?$/.exec(name);
    return [...list].sort((a, b) => {
      const pa = parse(a.name);
      const pb = parse(b.name);
      if (pa && pb) {
        return (
          pa[1].localeCompare(pb[1]) ||
          +pa[2] - +pb[2] ||
          +(pa[3] ?? 0) - +(pb[3] ?? 0)
        );
      }
      return a.name.localeCompare(b.name);
    });
  }

  // --- "add multiple" modal ---

  readonly modalOpen = signal(false);
  readonly saving = signal(false);
  readonly mTypeId = signal<string>('');
  readonly mCount = signal(1);
  readonly mSelfPayTypeId = signal<string>('');
  readonly mAllowMultipleUsers = signal(false);
  /** Tables run a tab by default; bars pay as they order. */
  readonly mKeepOpen = signal(true);

  /** Picking the type presets "Keep open": off for BAR, on for everything else. */
  selectModalType(typeId: string): void {
    this.mTypeId.set(typeId);
    this.mKeepOpen.set(this.typeName(typeId) !== 'BAR');
  }
  readonly mPaymentTypeIds = signal<string[]>([]);
  readonly mMenuId = signal<string>('');
  readonly mServiceId = signal<string>('');
  readonly mPrinterId = signal<string>('');
  readonly mCashRegisterId = signal<string>('');

  readonly menuOptions = computed(() => this.menus());
  readonly printerOptions = computed(() =>
    this.integrations().filter((i) => i.type === 'PRINTER'),
  );
  readonly cashRegisterOptions = computed(() =>
    this.integrations().filter((i) => i.type === 'CASH_REGISTER'),
  );

  openModal(): void {
    this.mTypeId.set('');
    this.mCount.set(1);
    this.mSelfPayTypeId.set('');
    this.mAllowMultipleUsers.set(false);
    this.mPaymentTypeIds.set([]);
    this.mMenuId.set('');
    this.mServiceId.set('');
    this.mPrinterId.set('');
    this.mCashRegisterId.set('');
    this.error.set(null);
    this.modalOpen.set(true);
  }

  closeModal(): void {
    this.modalOpen.set(false);
  }

  saveBatch(): void {
    if (!this.mTypeId()) {
      this.error.set('Please select an order point type.');
      return;
    }
    if (!this.mCount() || this.mCount() < 1) {
      this.error.set('Please enter how many order points to add.');
      return;
    }
    const batch: CreateOrderPointsBatch = {
      locationId: this.locationFilter(),
      typeId: this.mTypeId(),
      count: this.mCount(),
      selfPayTypeId: this.mSelfPayTypeId() || null,
      allowMultipleUsers: this.mAllowMultipleUsers(),
      keepOpen: this.mKeepOpen(),
      paymentTypeIds: this.mPaymentTypeIds(),
      menuId: this.mMenuId() || null,
      serviceOrderPointId: this.mServiceId() || null,
      printerId: this.mPrinterId() || null,
      cashRegisterId: this.mCashRegisterId() || null,
    };
    this.saving.set(true);
    this.orderPointService.createBatch(batch).subscribe({
      next: (created) => {
        this.orderPoints.update((list) => this.sorted([...list, ...created]));
        this.saving.set(false);
        this.modalOpen.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(err.status === 400 ? 'Please check the fields.' : 'Failed to add order points.');
      },
    });
  }

  // --- inline edit ---

  readonly editingId = signal<string | null>(null);
  readonly editName = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  readonly editNickname = new FormControl('', { nonNullable: true });
  readonly editSelfOrderMode = signal<SelfOrderMode>('CONFIRM');
  readonly selfOrderModeOptions = [
    { id: 'ALLOW', name: 'Allowed' },
    { id: 'CONFIRM', name: 'Confirm' },
    { id: 'DISALLOW', name: 'Disabled' },
  ];
  selfOrderModeLabel(mode: SelfOrderMode): string {
    return this.selfOrderModeOptions.find((o) => o.id === mode)?.name ?? mode;
  }
  readonly editSelfPayTypeId = signal<string>('');
  readonly editAllowMultipleUsers = signal(false);
  readonly editKeepOpen = signal(true);
  readonly editPaymentTypeIds = signal<string[]>([]);
  readonly editMenuId = signal<string>('');
  readonly editServiceId = signal<string>('');
  readonly editPrinterId = signal<string>('');
  readonly editCashRegisterId = signal<string>('');

  /** Service-point options for the row being edited — a point cannot serve itself. */
  readonly editServiceOptions = computed(() =>
    this.serviceOptions().filter((p) => p.id !== this.editingId()),
  );

  startEdit(point: OrderPoint): void {
    this.editingId.set(point.id);
    this.editName.setValue(point.name);
    this.editNickname.setValue(point.nickname ?? '');
    this.editSelfOrderMode.set(point.selfOrderMode ?? 'CONFIRM');
    this.editSelfPayTypeId.set(point.selfPayTypeId ?? '');
    this.editAllowMultipleUsers.set(point.allowMultipleUsers);
    this.editKeepOpen.set(point.keepOpen);
    this.editPaymentTypeIds.set([...point.paymentTypeIds]);
    this.editMenuId.set(point.menuId ?? '');
    this.editServiceId.set(point.serviceOrderPointId ?? '');
    this.editPrinterId.set(point.printerId ?? '');
    this.editCashRegisterId.set(point.cashRegisterId ?? '');
    this.error.set(null);
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  saveEdit(id: string): void {
    if (this.editName.invalid) {
      this.error.set('Please enter a name.');
      return;
    }
    const input: OrderPointInput = {
      name: this.editName.value.trim(),
      nickname: this.editNickname.value.trim() || null,
      selfOrderMode: this.editSelfOrderMode(),
      selfPayTypeId: this.editSelfPayTypeId() || null,
      allowMultipleUsers: this.editAllowMultipleUsers(),
      keepOpen: this.editKeepOpen(),
      paymentTypeIds: this.editPaymentTypeIds(),
      menuId: this.editMenuId() || null,
      serviceOrderPointId: this.editServiceId() || null,
      printerId: this.editPrinterId() || null,
      cashRegisterId: this.editCashRegisterId() || null,
    };
    this.orderPointService.update(id, input).subscribe({
      next: (updated) => {
        this.orderPoints.update((list) => this.sorted(list.map((p) => (p.id === id ? updated : p))));
        this.editingId.set(null);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(err.status === 400 ? 'Please check the fields.' : 'Failed to update order point.');
      },
    });
  }

  // --- split (tables only) ---

  /** id of the table whose split call is in flight. */
  readonly splitting = signal<string | null>(null);

  /** Only TABLE points named T{n}.{m} can be split into a new slot. */
  canSplit(point: OrderPoint): boolean {
    return this.typeName(point.typeId) === 'TABLE' && /^[A-Za-z]+\d+\.\d+$/.test(point.name);
  }

  split(point: OrderPoint): void {
    if (this.splitting()) {
      return;
    }
    this.error.set(null);
    this.splitting.set(point.id);
    this.orderPointService.split(point.id).subscribe({
      next: (slot) => {
        this.orderPoints.update((list) => this.sorted([...list, slot]));
        this.splitting.set(null);
      },
      error: () => {
        this.error.set(`Could not split ${point.name}.`);
        this.splitting.set(null);
      },
    });
  }

  // --- delete ---

  readonly pendingDelete = signal<OrderPoint | null>(null);

  remove(point: OrderPoint): void {
    this.error.set(null);
    this.pendingDelete.set(point);
  }

  cancelDelete(): void {
    this.pendingDelete.set(null);
  }

  confirmDelete(): void {
    const point = this.pendingDelete();
    if (!point) {
      return;
    }
    this.pendingDelete.set(null);
    this.orderPointService.delete(point.id).subscribe({
      next: () => this.orderPoints.update((list) => list.filter((p) => p.id !== point.id)),
      error: (err: HttpErrorResponse) =>
        this.error.set(
          err.status === 409
            ? `${point.name} has an open session — close the table first.`
            : 'Failed to delete order point.',
        ),
    });
  }
}
