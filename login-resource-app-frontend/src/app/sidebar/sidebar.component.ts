import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../services/auth.service';

// The one sidebar shown on every page after login (see LayoutComponent). It greets the
// signed-in account and lists only the navigation links relevant to that account's role:
// Admin gets Resource / Task Master / Records; a resource person gets Assigned Task /
// Report Task. A Logout key sits pinned to the bottom either way.
@Component({
  selector: 'app-sidebar',
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.css',
  imports: [CommonModule, RouterLink, RouterLinkActive]
})
export class SidebarComponent {
  constructor(private authService: AuthService, private router: Router) {}

  get isAdmin(): boolean {
    return this.authService.isAdmin();
  }

  // "Admin" for the admin account, otherwise the resource person's own name
  // (e.g. "Hi, Priya Sharma") straight from what they logged in as.
  get greetingName(): string {
    return this.isAdmin ? 'Admin' : this.authService.getResourceName();
  }

  get initial(): string {
    const name = this.greetingName.trim();
    return name ? name.charAt(0).toUpperCase() : '?';
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/']);
  }
}
