import { Component, OnDestroy, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../../core/auth.service';
import { AppLogo } from '../../../shared/logo.component';
import { SiteFooter } from '../../../shared/site-footer.component';

/** Landing route per role; the first matching role from the JWT wins. */
export const ROLE_HOME: Record<string, string> = {
  ADMIN: '/backoffice',
  SUPER: '/backoffice',
  SERVICE: '/service',
  WAITER: '/waiter',
  WATCHER: '/watcher',
};
export const DEFAULT_HOME = '/backoffice';

/** The landing route for a freshly signed-in user. */
export function homeFor(roles: string[]): string {
  return roles.map((r) => ROLE_HOME[r]).find((route) => route) ?? DEFAULT_HOME;
}

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, AppLogo, SiteFooter],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login implements OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  // Lock zoom (pinch / double-tap) while the login form is shown.
  private readonly viewportMeta = document.querySelector('meta[name="viewport"]');
  private readonly previousViewport = this.viewportMeta?.getAttribute('content') ?? '';

  constructor() {
    this.viewportMeta?.setAttribute(
      'content',
      'width=device-width, initial-scale=1, maximum-scale=1, minimum-scale=1, user-scalable=no',
    );
  }

  ngOnDestroy(): void {
    this.viewportMeta?.setAttribute('content', this.previousViewport);
  }

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly submitted = signal(false);

  readonly form = this.fb.nonNullable.group({
    username: ['', [Validators.required]],
    password: ['', [Validators.required]],
    rememberMe: [false],
  });

  submit(): void {
    this.submitted.set(true);
    if (this.form.invalid) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);

    const { username, password } = this.form.getRawValue();
    const previousUser = this.auth.session()?.username ?? null;
    this.auth.login({ username, password }).subscribe({
      next: (res) => {
        this.loading.set(false);
        const target = homeFor(res.roles);
        if (previousUser && previousUser !== res.username) {
          // Signed in on top of a different user — full reload so nothing of the
          // previous session survives in memory.
          window.location.replace(target);
        } else {
          this.router.navigateByUrl(target);
        }
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(
          err.status === 401 || err.status === 403
            ? 'Invalid username or password.'
            : 'Unable to sign in. Please try again.',
        );
      },
    });
  }
}
