import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { QrTemplate, QrTemplateService } from './qr-template.service';
import { ConfirmDialog } from '../../../../shared/confirm-dialog/confirm-dialog';

/** Catalog of QR frames: a background image plus where the QR code and the order point name go. */
@Component({
  selector: 'app-qr-templates-page',
  imports: [ReactiveFormsModule, ConfirmDialog, DecimalPipe],
  templateUrl: './qr-templates-page.html',
  styleUrl: './qr-templates-page.scss',
})
export class QrTemplatesPage {
  private readonly templateService = inject(QrTemplateService);
  private readonly fb = inject(FormBuilder);

  readonly templates = signal<QrTemplate[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  /** Whether the blank "new template" row is being added. */
  readonly draft = signal(false);
  /** id of the existing row being edited, or null. */
  readonly editingId = signal<string | null>(null);
  /** template pending delete confirmation, or null when the dialog is closed. */
  readonly pendingDelete = signal<QrTemplate | null>(null);

  readonly draftName = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  readonly editName = new FormControl('', { nonNullable: true, validators: [Validators.required] });
  /** Frame geometry while editing — percentages of the image (what the user sees), fractions on the wire. */
  readonly editGeometry = this.fb.nonNullable.group({
    qrX: [30, [Validators.required, Validators.min(0), Validators.max(100)]],
    qrY: [30, [Validators.required, Validators.min(0), Validators.max(100)]],
    qrSize: [40, [Validators.required, Validators.min(1), Validators.max(100)]],
    qrColor: ['#000000', [Validators.required, Validators.pattern(/^#[0-9a-fA-F]{6}$/)]],
    title: [''],
    titleY: [10, [Validators.required, Validators.min(0), Validators.max(100)]],
    labelY: [90, [Validators.required, Validators.min(0), Validators.max(100)]],
    labelSize: [8, [Validators.required, Validators.min(0.5), Validators.max(100)]],
    labelColor: ['#FFFFFF', [Validators.required, Validators.pattern(/^#[0-9a-fA-F]{6}$/)]],
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.templateService.list().subscribe({
      next: (templates) => {
        this.templates.set(templates);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load QR templates.');
        this.loading.set(false);
      },
    });
  }

  // --- create (inline draft row) ---

  startCreate(): void {
    this.editingId.set(null);
    this.draftName.reset();
    this.error.set(null);
    this.draft.set(true);
  }

  cancelCreate(): void {
    this.draft.set(false);
  }

  saveCreate(): void {
    if (this.draftName.invalid) {
      return;
    }
    this.templateService.create({ name: this.draftName.value.trim() }).subscribe({
      next: (template) => {
        this.templates.update((list) => this.sorted([...list, template]));
        this.draft.set(false);
      },
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'create')),
    });
  }

  // --- edit existing ---

  startEdit(template: QrTemplate): void {
    this.draft.set(false);
    this.editingId.set(template.id);
    this.editName.setValue(template.name);
    this.editGeometry.setValue({
      qrX: pct(template.qrX),
      qrY: pct(template.qrY),
      qrSize: pct(template.qrSize),
      qrColor: template.qrColor,
      title: template.title ?? '',
      titleY: pct(template.titleY),
      labelY: pct(template.labelY),
      labelSize: pct(template.labelSize),
      labelColor: template.labelColor,
    });
    this.error.set(null);
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  saveEdit(id: string): void {
    if (this.editName.invalid || this.editGeometry.invalid) {
      this.error.set('Please check the frame values (percentages 0–100, colour as #RRGGBB).');
      return;
    }
    const g = this.editGeometry.getRawValue();
    this.templateService
      .update(id, {
        name: this.editName.value.trim(),
        qrX: frac(g.qrX),
        qrY: frac(g.qrY),
        qrSize: frac(g.qrSize),
        qrColor: g.qrColor.toUpperCase(),
        title: g.title.trim(),
        titleY: frac(g.titleY),
        labelY: frac(g.labelY),
        labelSize: frac(g.labelSize),
        labelColor: g.labelColor.toUpperCase(),
      })
      .subscribe({
      next: (updated) => {
        this.templates.update((list) => this.sorted(list.map((t) => (t.id === id ? updated : t))));
        this.editingId.set(null);
      },
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'update')),
    });
  }

  /** Live geometry for the preview of the row being edited (falls back to the saved values). */
  previewOf(template: QrTemplate): QrTemplate {
    if (this.editingId() !== template.id) {
      return template;
    }
    const g = this.editGeometry.getRawValue();
    return {
      ...template,
      qrX: frac(g.qrX),
      qrY: frac(g.qrY),
      qrSize: frac(g.qrSize),
      qrColor: /^#[0-9a-fA-F]{6}$/.test(g.qrColor) ? g.qrColor : template.qrColor,
      title: g.title.trim() || null,
      titleY: frac(g.titleY),
      labelY: frac(g.labelY),
      labelSize: frac(g.labelSize),
      labelColor: /^#[0-9a-fA-F]{6}$/.test(g.labelColor) ? g.labelColor : template.labelColor,
    };
  }

  // --- image ---

  /** Per-template cache-buster, bumped after each image change. */
  private readonly imageVersion = signal<Record<string, number>>({});
  readonly uploadingImage = signal<string | null>(null);

  imageUrl(template: QrTemplate): string {
    return this.templateService.imageUrl(template.id, this.imageVersion()[template.id] ?? 0);
  }

  onImageSelected(template: QrTemplate, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = ''; // allow re-selecting the same file later
    if (!file) {
      return;
    }
    this.error.set(null);
    this.uploadingImage.set(template.id);
    this.templateService.uploadImage(template.id, file).subscribe({
      next: (updated) => {
        this.bumpImage(updated.id);
        this.templates.update((list) => list.map((t) => (t.id === updated.id ? updated : t)));
        this.uploadingImage.set(null);
      },
      error: (err: HttpErrorResponse) => {
        this.uploadingImage.set(null);
        this.error.set(err.status === 415 ? 'Please choose an image file.' : 'Failed to upload template image.');
      },
    });
  }

  removeImage(template: QrTemplate): void {
    this.error.set(null);
    this.templateService.deleteImage(template.id).subscribe({
      next: (updated) => {
        this.bumpImage(updated.id);
        this.templates.update((list) => list.map((t) => (t.id === updated.id ? updated : t)));
      },
      error: () => this.error.set('Failed to remove template image.'),
    });
  }

  private bumpImage(id: string): void {
    this.imageVersion.update((m) => ({ ...m, [id]: (m[id] ?? 0) + 1 }));
  }

  // --- delete ---

  remove(template: QrTemplate): void {
    this.error.set(null);
    this.pendingDelete.set(template);
  }

  cancelDelete(): void {
    this.pendingDelete.set(null);
  }

  confirmDelete(): void {
    const template = this.pendingDelete();
    if (!template) {
      return;
    }
    this.pendingDelete.set(null);
    this.templateService.delete(template.id).subscribe({
      next: () => this.templates.update((list) => list.filter((t) => t.id !== template.id)),
      error: (err: HttpErrorResponse) => this.error.set(this.message(err, 'delete')),
    });
  }

  private sorted(list: QrTemplate[]): QrTemplate[] {
    return [...list].sort((a, b) => a.name.localeCompare(b.name));
  }

  private message(err: HttpErrorResponse, action: string): string {
    if (err.status === 409) {
      return 'A QR template with that name already exists.';
    }
    if (err.status === 400) {
      return 'Please check the frame values (percentages 0–100, colour as #RRGGBB).';
    }
    return `Failed to ${action} QR template.`;
  }
}

/** Fraction of the image → percentage shown in the form (1 decimal). */
function pct(fraction: number): number {
  return Math.round(fraction * 1000) / 10;
}

/** Percentage from the form → fraction on the wire (4 decimals). */
function frac(percent: number): number {
  return Math.round(percent * 100) / 10000;
}
