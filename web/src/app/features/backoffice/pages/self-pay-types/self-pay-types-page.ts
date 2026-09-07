import { Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { SelfPayType, SelfPayTypeService } from './self-pay-type.service';
import { ConfirmDialog } from '../../../../shared/confirm-dialog/confirm-dialog';

@Component({
  selector: 'app-self-pay-types-page',
  imports: [ReactiveFormsModule, ConfirmDialog],
  templateUrl: './self-pay-types-page.html',
  styleUrl: './self-pay-types-page.scss',
})
export class SelfPayTypesPage {
  private readonly typeService = inject(SelfPayTypeService);

  readonly types = signal<SelfPayType[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  /** Whether the blank "new type" row is being added. */
  readonly draft = signal(false);
  /** id of the existing row being edited, or null. */
  readonly editingId = signal<string | null>(null);
  /** type pending delete confirmation, or null when the dialog is closed. */
  readonly pendingDelete = signal<SelfPayType | null>(null);

  readonly draftType = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  readonly editType = new FormControl('', { nonNullable: true, validators: [Validators.required] });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.typeService.list().subscribe({
      next: (types) => {
        this.types.set(types);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load self pay types.');
        this.loading.set(false);
      },
    });
  }

  // --- create (inline draft row) ---

  startCreate(): void {
    this.editingId.set(null);
    this.draftType.reset();
    this.error.set(null);
    this.draft.set(true);
  }

  cancelCreate(): void {
    this.draft.set(false);
  }

  saveCreate(): void {
    if (this.draftType.invalid) {
      return;
    }
    this.typeService.create(this.draftType.value.trim()).subscribe({
      next: (type) => {
        this.types.update((list) => this.sorted([...list, type]));
        this.draft.set(false);
      },
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'create')),
    });
  }

  // --- edit existing ---

  startEdit(type: SelfPayType): void {
    this.draft.set(false);
    this.editingId.set(type.id);
    this.editType.setValue(type.type);
    this.error.set(null);
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  saveEdit(id: string): void {
    if (this.editType.invalid) {
      return;
    }
    this.typeService.update(id, this.editType.value.trim()).subscribe({
      next: (updated) => {
        this.types.update((list) => this.sorted(list.map((t) => (t.id === id ? updated : t))));
        this.editingId.set(null);
      },
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'update')),
    });
  }

  // --- delete ---

  remove(type: SelfPayType): void {
    this.error.set(null);
    this.pendingDelete.set(type);
  }

  cancelDelete(): void {
    this.pendingDelete.set(null);
  }

  confirmDelete(): void {
    const type = this.pendingDelete();
    if (!type) {
      return;
    }
    this.pendingDelete.set(null);
    this.typeService.delete(type.id).subscribe({
      next: () => this.types.update((list) => list.filter((t) => t.id !== type.id)),
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'delete')),
    });
  }

  private sorted(list: SelfPayType[]): SelfPayType[] {
    return [...list].sort((a, b) => a.type.localeCompare(b.type));
  }

  private message(err: HttpErrorResponse, action: string): string {
    if (err.status === 409) {
      return 'A self pay type with that name already exists.';
    }
    return `Failed to ${action} self pay type.`;
  }
}
