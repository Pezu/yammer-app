import { AfterViewInit, Component, ElementRef, input, output, viewChild } from '@angular/core';

/**
 * Compact rich-text (HTML) editor: size, bold, italic, superscript, subscript.
 * Emits the raw HTML on every input; consumers render it with [innerHTML]
 * (Angular's sanitizer applies, so no stored-XSS bypass).
 */
@Component({
  selector: 'app-rich-text',
  template: `
    <div class="rte">
      <div class="rte-toolbar">
        <select class="rte-select" #fs (change)="execFontSize(fs.value); fs.value = ''" title="Font size">
          <option value="">Size</option>
          <option value="1">Small</option>
          <option value="3">Normal</option>
          <option value="5">Large</option>
          <option value="7">X-Large</option>
        </select>
        <button type="button" class="rte-btn" (click)="exec('bold')" title="Bold"><b>B</b></button>
        <button type="button" class="rte-btn" (click)="exec('italic')" title="Italic"><i>I</i></button>
        <button type="button" class="rte-btn" (click)="exec('superscript')" title="Superscript (up)">x<sup>2</sup></button>
        <button type="button" class="rte-btn" (click)="exec('subscript')" title="Subscript (down)">x<sub>2</sub></button>
      </div>
      <div
        #editor
        class="rte-editor"
        contenteditable="true"
        [attr.data-placeholder]="placeholder()"
        (input)="emit()"
        (paste)="onPaste($event)"
      ></div>
    </div>
  `,
  styles: [
    `
      .rte {
        width: 100%;
      }
      .rte-toolbar {
        display: flex;
        align-items: center;
        gap: 0.25rem;
        margin-bottom: 0.3rem;
      }
      .rte-btn {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-width: 26px;
        height: 26px;
        padding: 0 0.3rem;
        font: inherit;
        font-size: 0.8rem;
        color: var(--text);
        background: #fff;
        border: 1px solid var(--border);
        border-radius: 4px;
        cursor: pointer;
      }
      .rte-btn:hover {
        background: var(--page-bg);
      }
      .rte-btn sup,
      .rte-btn sub {
        font-size: 0.6em;
      }
      .rte-select {
        height: 26px;
        padding: 0 0.2rem;
        font: inherit;
        font-size: 0.75rem;
        color: var(--text);
        background: #fff;
        border: 1px solid var(--border);
        border-radius: 4px;
      }
      .rte-editor {
        min-height: 34px;
        padding: 0.4rem 0.7rem;
        font-size: 0.9rem;
        color: var(--text);
        background: #fff;
        border: 1px solid var(--border);
        border-radius: 6px;
      }
      .rte-editor:focus {
        outline: none;
        border-color: var(--primary);
        box-shadow: 0 0 0 0.2rem rgba(52, 84, 209, 0.15);
      }
      .rte-editor:empty::before {
        content: attr(data-placeholder);
        color: var(--muted);
      }
    `,
  ],
})
export class RichTextEditor implements AfterViewInit {
  /** Initial HTML — seeded once; later edits flow out via valueChange. */
  readonly value = input<string>('');
  readonly placeholder = input('');
  readonly valueChange = output<string>();

  private readonly editor = viewChild.required<ElementRef<HTMLDivElement>>('editor');

  ngAfterViewInit(): void {
    this.editor().nativeElement.innerHTML = this.value();
  }

  exec(command: string): void {
    document.execCommand(command);
    this.editor().nativeElement.focus();
    this.emit();
  }

  execFontSize(size: string): void {
    if (size) {
      document.execCommand('fontSize', false, size);
    }
    this.editor().nativeElement.focus();
    this.emit();
  }

  onPaste(event: ClipboardEvent): void {
    event.preventDefault();
    const text = event.clipboardData?.getData('text/plain') ?? '';
    document.execCommand('insertText', false, text);
    this.emit();
  }

  emit(): void {
    this.valueChange.emit(this.editor().nativeElement.innerHTML);
  }
}
