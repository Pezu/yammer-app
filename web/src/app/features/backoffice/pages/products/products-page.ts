import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Product, ProductInput, ProductService } from './product.service';
import { Client, ClientService } from '../clients/client.service';
import { Location, LocationService } from '../locations/location.service';
import { VatService, VatType } from '../vat/vat.service';
import { AuthService } from '../../../../core/auth.service';
import { ConfirmDialog } from '../../../../shared/confirm-dialog/confirm-dialog';
import { ComboBox } from '../../../../shared/combo-box';
import { RichTextEditor } from '../../../../shared/rich-text-editor';

/** The location's product catalog — the single source of truth menus point at. */
@Component({
  selector: 'app-products-page',
  imports: [ReactiveFormsModule, ConfirmDialog, ComboBox, RichTextEditor],
  templateUrl: './products-page.html',
  styleUrl: './products-page.scss',
})
export class ProductsPage {
  private readonly fb = inject(FormBuilder);
  private readonly productService = inject(ProductService);
  private readonly clientService = inject(ClientService);
  private readonly locationService = inject(LocationService);
  private readonly vatService = inject(VatService);
  private readonly auth = inject(AuthService);

  readonly isSuper = this.auth.isSuper;
  readonly ownClientId = computed(() => (this.isSuper() ? '' : this.auth.clientId() ?? ''));

  readonly products = signal<Product[]>([]);
  readonly clients = signal<Client[]>([]);
  readonly locations = signal<Location[]>([]);
  readonly vatTypes = signal<VatType[]>([]);
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

  readonly vatOptions = computed(() => this.vatTypes().map((v) => ({ id: v.id, name: `${v.value}%` })));
  private readonly vatById = computed(() => new Map(this.vatTypes().map((v) => [v.id, `${v.value}%`])));

  vatLabel(id: string | null): string {
    return (id && this.vatById().get(id)) || '—';
  }

  imageUrl(object: string): string {
    return this.productService.imageUrl(object);
  }

  constructor() {
    this.vatService.list().subscribe({ next: (v) => this.vatTypes.set(v) });
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
        this.products.set([]);
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
    this.productService.list(locationId).subscribe({
      next: (products) => {
        this.products.set(products);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load products.');
        this.loading.set(false);
      },
    });
  }

  private locationsFor(clientId: string): Location[] {
    return clientId ? this.locations().filter((l) => l.clientId === clientId) : [];
  }

  // --- create (inline draft row) ---

  readonly draft = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly pendingDelete = signal<Product | null>(null);
  readonly draftVatId = signal<string>('');
  readonly editVatId = signal<string>('');
  /** Rich-HTML product names (size/bold/italic/sup/sub). */
  readonly draftName = signal<string>('');
  readonly editName = signal<string>('');

  readonly draftForm = this.newForm();
  readonly editForm = this.newForm();

  startCreate(): void {
    this.editingId.set(null);
    this.draftForm.reset();
    this.draftName.set('');
    this.draftVatId.set('');
    this.error.set(null);
    this.draft.set(true);
  }

  cancelCreate(): void {
    this.draft.set(false);
  }

  saveCreate(): void {
    if (!this.hasText(this.draftName())) {
      this.error.set('Please enter a name.');
      return;
    }
    this.productService
      .create(this.input(this.draftName(), this.draftForm.getRawValue(), this.draftVatId(), null))
      .subscribe({
        next: (product) => {
          // last introduced goes to the top of the list
          this.products.update((list) => [product, ...list]);
          this.draft.set(false);
        },
        error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'create')),
      });
  }

  // --- edit existing ---

  startEdit(product: Product): void {
    this.draft.set(false);
    this.editingId.set(product.id);
    this.editForm.setValue({
      description: product.description ?? '',
    });
    this.editName.set(product.name);
    this.editVatId.set(product.vatTypeId ?? '');
    this.error.set(null);
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  saveEdit(product: Product): void {
    if (!this.hasText(this.editName())) {
      this.error.set('Please enter a name.');
      return;
    }
    this.productService
      .update(
        product.id,
        this.input(this.editName(), this.editForm.getRawValue(), this.editVatId(), product.imageObject),
      )
      .subscribe({
        next: (updated) => {
          this.products.update((list) => list.map((p) => (p.id === updated.id ? updated : p)));
          this.editingId.set(null);
        },
        error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'update')),
      });
  }

  // --- image (existing rows only) ---

  readonly uploadingImage = signal<string | null>(null);

  onImageSelected(product: Product, input: HTMLInputElement): void {
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    this.error.set(null);
    this.uploadingImage.set(product.id);
    this.productService.uploadImage(file).subscribe({
      next: ({ object }) => this.applyImage(product, object),
      error: () => {
        this.uploadingImage.set(null);
        this.error.set('Failed to upload image.');
      },
    });
  }

  removeImage(product: Product): void {
    this.uploadingImage.set(product.id);
    this.applyImage(product, null);
  }

  private applyImage(product: Product, object: string | null): void {
    const input: ProductInput = {
      locationId: product.locationId,
      name: product.name,
      description: product.description ?? '',
      vatTypeId: product.vatTypeId,
      imageObject: object,
    };
    this.productService.update(product.id, input).subscribe({
      next: (updated) => {
        this.products.update((list) => list.map((p) => (p.id === updated.id ? updated : p)));
        this.uploadingImage.set(null);
      },
      error: () => {
        this.uploadingImage.set(null);
        this.error.set('Failed to update image.');
      },
    });
  }

  // --- delete ---

  remove(product: Product): void {
    this.error.set(null);
    this.pendingDelete.set(product);
  }

  cancelDelete(): void {
    this.pendingDelete.set(null);
  }

  confirmDelete(): void {
    const product = this.pendingDelete();
    if (!product) {
      return;
    }
    this.pendingDelete.set(null);
    this.productService.delete(product.id).subscribe({
      next: () => this.products.update((list) => list.filter((p) => p.id !== product.id)),
      error: () => this.error.set('Failed to delete product.'),
    });
  }

  private newForm() {
    return this.fb.nonNullable.group({
      description: [''],
    });
  }

  private input(
    name: string,
    raw: { description: string },
    vatTypeId: string,
    imageObject: string | null,
  ): ProductInput {
    return {
      locationId: this.locationFilter(),
      name,
      description: raw.description,
      vatTypeId: vatTypeId || null,
      imageObject,
    };
  }

  /** True when the rich-HTML value has visible text (ignoring markup). */
  private hasText(html: string): boolean {
    const tmp = document.createElement('div');
    tmp.innerHTML = html;
    return (tmp.textContent ?? '').trim().length > 0;
  }

  private message(err: HttpErrorResponse, action: string): string {
    if (err.status === 400) {
      return 'Please check the fields.';
    }
    return `Failed to ${action} product.`;
  }
}
