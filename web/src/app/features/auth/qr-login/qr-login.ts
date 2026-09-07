import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth.service';
import { AppLogo } from '../../../shared/logo.component';
import { homeFor } from '../login/login';

/** Landing route for a scanned QR-login code — exchanges the token for a session. */
@Component({
  selector: 'app-qr-login',
  imports: [RouterLink, AppLogo],
  template: `
    <main class="auth">
      <div class="card">
        <div class="logo"><app-logo [height]="110" /></div>
        @if (failed()) {
          <div class="error" role="alert">This QR code is not valid.</div>
          <a class="link" routerLink="/login">Go to login</a>
        } @else {
          <p class="status"><span class="spinner" aria-hidden="true"></span> Signing you in…</p>
        }
      </div>
    </main>
  `,
  styles: [
    `
      :host {
        display: flex;
        flex-direction: column;
        min-height: 100vh;
        min-height: 100dvh;
      }
      .auth {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 1.5rem;
        background: #fff;
      }
      .card {
        width: 100%;
        max-width: 440px;
        padding: 2.5rem;
        text-align: center;
        background: #fff;
        border: 1px solid var(--border);
        border-radius: 10px;
        box-shadow: 0 1rem 3rem rgba(40, 60, 80, 0.1);
      }
      .logo {
        display: flex;
        justify-content: center;
        margin: 0 0 1.75rem;
      }
      .error {
        margin-bottom: 1rem;
        padding: 0.55rem 0.85rem;
        font-size: 0.8rem;
        color: var(--danger);
        background: rgba(234, 77, 77, 0.1);
        border: 1px solid rgba(234, 77, 77, 0.25);
        border-radius: 6px;
      }
      .link {
        font-size: 0.85rem;
        font-weight: 600;
        color: var(--primary);
        text-decoration: none;
      }
      .link:hover {
        text-decoration: underline;
      }
      .status {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        margin: 0;
        color: var(--muted);
      }
      .spinner {
        width: 1rem;
        height: 1rem;
        border: 2px solid var(--border);
        border-top-color: var(--primary);
        border-radius: 50%;
        animation: spin 0.6s linear infinite;
      }
      @keyframes spin {
        to {
          transform: rotate(360deg);
        }
      }
    `,
  ],
})
export class QrLogin {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly failed = signal(false);

  constructor() {
    const token = this.route.snapshot.paramMap.get('token') ?? '';
    const previousUser = this.auth.session()?.username ?? null;
    this.auth.qrLogin(token).subscribe({
      next: (res) => {
        const target = homeFor(res.roles);
        if (previousUser && previousUser !== res.username) {
          // Scanned on top of a different user's session — full reload so nothing
          // of the previous session survives in memory.
          window.location.replace(target);
        } else {
          this.router.navigateByUrl(target);
        }
      },
      error: () => this.failed.set(true),
    });
  }
}
