import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

// Guards the two resource-person pages (Assigned Task / Report Task). The admin account
// hitting one of these URLs directly is bounced to the Resource page instead.
export const userGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isAdmin()) {
    return true;
  }
  router.navigate(['/resources']);
  return false;
};
