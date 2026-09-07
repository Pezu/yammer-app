import { Component, computed, effect, inject, signal } from '@angular/core';
import { RecipeComponentInput, RecipeService } from './recipe.service';
import { Product, ProductService } from '../products/product.service';
import { Client, ClientService } from '../clients/client.service';
import { Location, LocationService, defaultLocation } from '../locations/location.service';
import { AuthService } from '../../../../core/auth.service';
import { ComboBox } from '../../../../shared/combo-box';

/** An editable recipe row (component + fraction consumed). */
interface RecipeRow {
  componentProductId: string;
  quantity: number | null;
}

/**
 * A product's recipe: which other products it consumes, in fractional quantities
 * (e.g. hugo = 0.0714 of a prosecco bottle + 0.01 of a syrup bottle) — so the
 * product summary can roll orders up into raw product consumption.
 */
@Component({
  selector: 'app-recipes-page',
  imports: [ComboBox],
  templateUrl: './recipes-page.html',
  styleUrl: './recipes-page.scss',
})
export class RecipesPage {
  private readonly recipeService = inject(RecipeService);
  private readonly productService = inject(ProductService);
  private readonly clientService = inject(ClientService);
  private readonly locationService = inject(LocationService);
  private readonly auth = inject(AuthService);

  readonly isSuper = this.auth.isSuper;
  readonly ownClientId = computed(() => (this.isSuper() ? '' : this.auth.clientId() ?? ''));

  readonly clients = signal<Client[]>([]);
  readonly locations = signal<Location[]>([]);
  readonly products = signal<Product[]>([]);
  readonly error = signal<string | null>(null);
  readonly saved = signal(false);

  readonly clientFilter = signal<string>('');
  readonly locationFilter = signal<string>('');
  readonly headerLocationOptions = computed(() =>
    this.locationsFor(this.isSuper() ? this.clientFilter() : this.ownClientId()),
  );
  readonly showClientCombo = this.isSuper;
  readonly showLocationCombo = computed(() => !this.isSuper() || !!this.clientFilter());

  /** The product whose recipe is being edited. */
  readonly productId = signal<string>('');
  readonly rows = signal<RecipeRow[]>([]);
  readonly loadingRecipe = signal(false);
  readonly saving = signal(false);

  readonly productOptions = computed(() => this.products().map((p) => ({ id: p.id, name: p.name })));
  /** Component options — every product except the one being defined. */
  readonly componentOptions = computed(() =>
    this.products()
      .filter((p) => p.id !== this.productId())
      .map((p) => ({ id: p.id, name: p.name })),
  );

  readonly canSave = computed(
    () =>
      !!this.productId() &&
      !this.saving() &&
      this.rows().every((r) => r.componentProductId && r.quantity !== null && r.quantity > 0),
  );

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
      const preferred = defaultLocation(this.headerLocationOptions());
      if (preferred && !this.locationFilter()) {
        this.locationFilter.set(preferred.id);
      }
    });
    effect(() => {
      const locationId = this.locationFilter();
      this.productId.set('');
      this.rows.set([]);
      if (locationId) {
        this.productService.list(locationId).subscribe({
          next: (products) => this.products.set(products),
          error: () => this.error.set('Failed to load products.'),
        });
      } else {
        this.products.set([]);
      }
    });
  }

  selectClient(id: string): void {
    this.clientFilter.set(id);
    this.locationFilter.set('');
  }

  private locationsFor(clientId: string): Location[] {
    return clientId ? this.locations().filter((l) => l.clientId === clientId) : [];
  }

  selectProduct(id: string): void {
    this.productId.set(id);
    this.saved.set(false);
    this.error.set(null);
    this.rows.set([]);
    if (!id) {
      return;
    }
    this.loadingRecipe.set(true);
    this.recipeService.get(id).subscribe({
      next: (components) => {
        this.rows.set(
          components.map((c) => ({ componentProductId: c.componentProductId, quantity: c.quantity })),
        );
        this.loadingRecipe.set(false);
      },
      error: () => {
        this.error.set('Failed to load the recipe.');
        this.loadingRecipe.set(false);
      },
    });
  }

  addRow(): void {
    this.saved.set(false);
    this.rows.update((rows) => [...rows, { componentProductId: '', quantity: null }]);
  }

  removeRow(index: number): void {
    this.saved.set(false);
    this.rows.update((rows) => rows.filter((_, i) => i !== index));
  }

  setComponent(index: number, id: string): void {
    this.saved.set(false);
    this.rows.update((rows) => rows.map((r, i) => (i === index ? { ...r, componentProductId: id } : r)));
  }

  setQuantity(index: number, value: string): void {
    this.saved.set(false);
    const quantity = value === '' ? null : +value;
    this.rows.update((rows) => rows.map((r, i) => (i === index ? { ...r, quantity } : r)));
  }

  save(): void {
    if (!this.canSave()) {
      return;
    }
    const payload: RecipeComponentInput[] = this.rows().map((r) => ({
      componentProductId: r.componentProductId,
      quantity: r.quantity as number,
    }));
    this.saving.set(true);
    this.error.set(null);
    this.recipeService.save(this.productId(), payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.saved.set(true);
      },
      error: () => {
        this.saving.set(false);
        this.error.set('Failed to save the recipe.');
      },
    });
  }
}
