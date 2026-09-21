import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

// Guards the three admin-only pages (Resource / Task Master / Records). A resource person
// hitting one of these URLs directly is bounced to their own Assigned Task page instead.
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAdmin()) {
    return true;
  }
  router.navigate(['/assigned-task']);
  return false;
};
