import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { User, UserQr, UserService } from './user.service';
import { RoleService } from '../roles/role.service';
import { Client, ClientService } from '../clients/client.service';
import { Location, LocationService } from '../locations/location.service';
import { AuthService, ROLE_SUPER } from '../../../../core/auth.service';
import { ConfirmDialog } from '../../../../shared/confirm-dialog/confirm-dialog';
import { ComboBox } from '../../../../shared/combo-box';

@Component({
  selector: 'app-users-page',
  imports: [ReactiveFormsModule, ConfirmDialog, ComboBox],
  templateUrl: './users-page.html',
  styleUrl: './users-page.scss',
})
export class UsersPage {
  private readonly fb = inject(FormBuilder);
  private readonly userService = inject(UserService);
  private readonly roleService = inject(RoleService);
  private readonly clientService = inject(ClientService);
  private readonly locationService = inject(LocationService);
  private readonly auth = inject(AuthService);

  /** Whether the logged-in user is SUPER (chooses any client) vs scoped to one. */
  readonly isSuper = this.auth.isSuper;
  /** For a non-SUPER (ADMIN) operator, the single client they manage (from the JWT). */
  readonly ownClientId = computed(() => (this.isSuper() ? '' : this.auth.clientId() ?? ''));

  readonly users = signal<User[]>([]);
  readonly availableRoles = signal<string[]>([]);
  readonly clients = signal<Client[]>([]);
  readonly locations = signal<Location[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  /** SUPER-only header filter: '' = all clients. */
  readonly clientFilter = signal<string>('');
  /** Combo search query. */
  readonly comboSearch = signal('');
  /** Combo options — matches the search, capped at the first 5. */
  readonly comboOptions = computed(() => {
    const q = this.comboSearch().trim().toLowerCase();
    const matches = q
      ? this.clients().filter((c) => c.name.toLowerCase().includes(q))
      : this.clients();
    return matches.slice(0, 5);
  });
  /** Header location filter ('' = all locations of the selected client). */
  readonly locationFilter = signal<string>('');
  /** Options for the header location combo — the context client's locations. */
  readonly headerLocationOptions = computed(() =>
    this.locationsFor(this.isSuper() ? this.clientFilter() : this.ownClientId()),
  );

  /** Users shown after the client + location filters (the API never lists SUPER accounts). */
  readonly visibleUsers = computed(() => {
    const clientFilter = this.clientFilter();
    const locationFilter = this.locationFilter();
    let users = this.users();
    if (clientFilter) {
      users = users.filter((u) => u.clientId === clientFilter);
    }
    if (locationFilter) {
      users = users.filter((u) => u.locationId === locationFilter);
    }
    return users;
  });

  /** SUPER must pick a client before the table is shown; others always see it. */
  readonly showTable = computed(() => !this.isSuper() || !!this.clientFilter());

  /** Custom client combo (styled dropdown, not a native select). */
  readonly comboOpen = signal(false);
  readonly selectedClientName = computed(
    () => this.clients().find((c) => c.id === this.clientFilter())?.name ?? 'Select a client…',
  );

  toggleCombo(): void {
    this.comboSearch.set('');
    this.comboOpen.update((open) => !open);
  }

  closeCombo(): void {
    this.comboOpen.set(false);
  }

  selectClient(id: string): void {
    this.clientFilter.set(id);
    this.locationFilter.set(''); // locations belong to the client — reset on switch
    this.comboOpen.set(false);
  }

  readonly draft = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly pendingDelete = signal<User | null>(null);

  /** Selected role names for the row being added / edited. */
  readonly draftRoles = signal<string[]>([]);
  readonly editRoles = signal<string[]>([]);

  /** Selected client id ('' = none) for the row being added / edited. */
  readonly draftClientId = signal<string>('');
  readonly editClientId = signal<string>('');

  /** Selected location id ('' = none) for the row being added / edited. */
  readonly draftLocationId = signal<string>('');
  readonly editLocationId = signal<string>('');

  /** Location options for the row being added / edited — the row's client's locations. */
  readonly draftLocationOptions = computed(() => this.locationsFor(this.draftClientId()));
  readonly editLocationOptions = computed(() => this.locationsFor(this.editClientId()));

  private readonly locationById = computed(
    () => new Map(this.locations().map((l) => [l.id, l.name])),
  );

  locationName(id: string | null): string {
    return (id && this.locationById().get(id)) || '—';
  }

  private locationsFor(clientId: string): Location[] {
    return clientId ? this.locations().filter((l) => l.clientId === clientId) : [];
  }

  // SUPER users have no client; the client picker is hidden/cleared for them.
  readonly draftIsSuper = computed(() => this.draftRoles().includes(ROLE_SUPER));
  readonly editIsSuper = computed(() => this.editRoles().includes(ROLE_SUPER));

  // password is required when creating, optional (blank = keep) when editing.
  readonly draftForm = this.newForm(true);
  readonly editForm = this.newForm(false);

  constructor() {
    this.load();
    this.roleService.list().subscribe({
      next: (roles) => this.availableRoles.set(roles.map((r) => r.role)),
    });
    this.clientService.list().subscribe({
      next: (clients) => {
        this.clients.set(clients);
        // If there's only one client to choose from, select it by default.
        if (this.isSuper() && clients.length === 1 && !this.clientFilter()) {
          this.clientFilter.set(clients[0].id);
        }
      },
    });
    this.locationService.list().subscribe({
      next: (locations) => this.locations.set(locations),
    });
    // When the context client has exactly one location, select it automatically;
    // with several the operator must pick one (the add button stays disabled until then).
    effect(() => {
      const options = this.headerLocationOptions();
      if (options.length === 1 && !this.locationFilter()) {
        this.locationFilter.set(options[0].id);
      }
    });
  }


  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.userService.list().subscribe({
      next: (users) => {
        this.users.set(users);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load users.');
        this.loading.set(false);
      },
    });
  }

  // --- create ---

  startCreate(): void {
    this.editingId.set(null);
    this.draftForm.reset();
    this.draftRoles.set([]);
    // Client and location come from context: SUPER uses the header filters,
    // ADMIN their own client (+ the header location filter when one is picked).
    this.draftClientId.set(this.isSuper() ? this.clientFilter() : this.ownClientId());
    this.draftLocationId.set(this.locationFilter());
    this.error.set(null);
    this.draft.set(true);
  }

  cancelCreate(): void {
    this.draft.set(false);
  }

  saveCreate(): void {
    if (this.draftForm.invalid) {
      this.error.set('Please enter at least a name or a username, and a valid email.');
      return;
    }
    if (!this.draftIsSuper() && !this.draftLocationId()) {
      this.error.set('Please select a location — every user needs one.');
      return;
    }
    // Client id is contextual; the backend validates/forces it per the caller's role.
    const clientId = this.draftClientId() || null;
    this.userService
      .create({
        ...this.draftForm.getRawValue(),
        roles: this.draftRoles(),
        clientId,
        locationId: this.draftLocationId() || null,
      })
      .subscribe({
        next: (user) => {
          this.users.update((list) => this.sorted([...list, user]));
          this.draft.set(false);
        },
        error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'create')),
      });
  }

  // --- edit ---

  startEdit(user: User): void {
    this.draft.set(false);
    this.editingId.set(user.id);
    this.editForm.setValue({
      username: user.username,
      name: user.name ?? '',
      password: '',
      phone: user.phone ?? '',
      email: user.email ?? '',
    });
    this.editRoles.set([...user.roles]);
    // ADMIN operators are locked to their own client; SUPER keeps the user's client.
    this.editClientId.set(this.isSuper() ? user.clientId ?? '' : this.ownClientId());
    this.editLocationId.set(user.locationId ?? '');
    this.error.set(null);
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  saveEdit(id: string): void {
    if (this.editForm.invalid) {
      this.error.set('Please enter a username and a valid email.');
      return;
    }
    if (!this.editIsSuper() && !this.editLocationId()) {
      this.error.set('Please select a location — every user needs one.');
      return;
    }
    const clientId = this.editClientId() || null;
    this.userService
      .update(id, {
        ...this.editForm.getRawValue(),
        roles: this.editRoles(),
        clientId,
        locationId: this.editLocationId() || null,
      })
      .subscribe({
        next: (updated) => {
          this.users.update((list) => this.sorted(list.map((u) => (u.id === id ? updated : u))));
          this.editingId.set(null);
        },
        error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'update')),
      });
  }

  // --- delete ---

  remove(user: User): void {
    this.error.set(null);
    this.pendingDelete.set(user);
  }

  cancelDelete(): void {
    this.pendingDelete.set(null);
  }

  confirmDelete(): void {
    const user = this.pendingDelete();
    if (!user) {
      return;
    }
    this.pendingDelete.set(null);
    this.userService.delete(user.id).subscribe({
      next: () => this.users.update((list) => list.filter((u) => u.id !== user.id)),
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'delete')),
    });
  }

  // --- QR login code ---

  /** User whose QR-login popup is open, or null. */
  readonly qrUser = signal<User | null>(null);
  /** The fetched QR-login code (url + base64 PNG), null while loading. */
  readonly qrData = signal<UserQr | null>(null);
  /** Briefly true after copying the login URL. */
  readonly qrCopied = signal(false);
  private qrCopiedTimer: ReturnType<typeof setTimeout> | null = null;

  showQr(user: User): void {
    this.error.set(null);
    this.qrUser.set(user);
    this.qrData.set(null);
    this.userService.loginQr(user.id).subscribe({
      next: (qr) => {
        if (this.qrUser()?.id === user.id) {
          this.qrData.set(qr);
        }
      },
      error: () => {
        this.qrUser.set(null);
        this.error.set('Failed to load the QR code.');
      },
    });
  }

  closeQr(): void {
    this.qrUser.set(null);
    this.qrData.set(null);
    this.qrCopied.set(false);
    this.confirmQrReset.set(false);
  }

  /** Whether the "reset QR" confirmation dialog is open. */
  readonly confirmQrReset = signal(false);

  resetQr(): void {
    const user = this.qrUser();
    this.confirmQrReset.set(false);
    if (!user) {
      return;
    }
    this.qrData.set(null); // back to the loading state while the new code is issued
    this.userService.resetQr(user.id).subscribe({
      next: (qr) => {
        if (this.qrUser()?.id === user.id) {
          this.qrData.set(qr);
        }
      },
      error: () => {
        this.qrUser.set(null);
        this.error.set('Failed to reset the QR code.');
      },
    });
  }

  copyQrUrl(): void {
    const url = this.qrData()?.url;
    if (!url) {
      return;
    }
    this.copyText(url);
    this.qrCopied.set(true);
    if (this.qrCopiedTimer) {
      clearTimeout(this.qrCopiedTimer);
    }
    this.qrCopiedTimer = setTimeout(() => this.qrCopied.set(false), 1500);
  }

  /** The Clipboard API needs a secure context; fall back to execCommand on plain-http LAN dev. */
  private copyText(text: string): void {
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(text).catch(() => this.copyFallback(text));
    } else {
      this.copyFallback(text);
    }
  }

  private copyFallback(text: string): void {
    const area = document.createElement('textarea');
    area.value = text;
    area.style.position = 'fixed';
    area.style.opacity = '0';
    document.body.appendChild(area);
    area.select();
    document.execCommand('copy');
    area.remove();
  }

  /** Roles shaped for the multi-select combo ({id, name}). */
  readonly roleOptions = computed(() => this.availableRoles().map((r) => ({ id: r, name: r })));

  private newForm(create: boolean) {
    const form = this.fb.nonNullable.group({
      username: [''],
      name: [''],
      password: [''],
      phone: [''],
      email: ['', [Validators.email]],
    });
    if (create) {
      // A new user needs at least a name or a username; a blank username/password
      // is generated by the backend (QR-only sign-in).
      form.addValidators(() =>
        form.controls.username.value.trim() || form.controls.name.value.trim()
          ? null
          : { identity: true },
      );
    }
    return form;
  }

  private sorted(list: User[]): User[] {
    return [...list].sort((a, b) => a.username.localeCompare(b.username));
  }

  private message(err: HttpErrorResponse, action: string): string {
    if (err.status === 409) {
      return 'A user with that username already exists.';
    }
    if (err.status === 400) {
      return 'Please check the fields.';
    }
    return `Failed to ${action} user.`;
  }
}
