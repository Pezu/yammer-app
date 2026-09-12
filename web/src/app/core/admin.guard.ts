import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { homeFor } from '../features/auth/login/login';

/** The backoffice is for ADMIN and SUPER; anyone else is sent to their own home. */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const roles = auth.roles;
  return roles.includes('ADMIN') || roles.includes('SUPER') ? true : router.parseUrl(homeFor(roles));
};
